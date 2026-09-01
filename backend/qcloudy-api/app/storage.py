from __future__ import annotations

import asyncio
import sqlite3
import statistics
import time
from contextlib import closing
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable


@dataclass(frozen=True, slots=True)
class EndedSale:
    auction_id: str
    ended_at_ms: int
    item_id: str
    variant_key: str
    price: float
    is_bin: bool
    parse_quality: str


class MarketStorage:
    def __init__(self, path: Path):
        self.path = path
        self._write_lock = asyncio.Lock()

    def _connect(self) -> sqlite3.Connection:
        connection = sqlite3.connect(self.path)
        connection.row_factory = sqlite3.Row
        connection.execute("PRAGMA journal_mode=WAL")
        connection.execute("PRAGMA foreign_keys=ON")
        connection.execute("PRAGMA busy_timeout=5000")
        return connection

    async def initialize(self) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)

        def create() -> None:
            with closing(self._connect()) as connection, connection:
                connection.executescript(
                    """
                    CREATE TABLE IF NOT EXISTS ended_sales (
                        auction_id TEXT PRIMARY KEY,
                        ended_at_ms INTEGER NOT NULL,
                        item_id TEXT NOT NULL,
                        variant_key TEXT NOT NULL,
                        price REAL NOT NULL CHECK(price > 0),
                        is_bin INTEGER NOT NULL CHECK(is_bin IN (0, 1)),
                        parse_quality TEXT NOT NULL,
                        ingested_at_ms INTEGER NOT NULL
                    );
                    CREATE INDEX IF NOT EXISTS idx_sales_lookup
                    ON ended_sales(item_id, variant_key, ended_at_ms);

                    CREATE TABLE IF NOT EXISTS coverage_gaps (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        started_at_ms INTEGER NOT NULL,
                        ended_at_ms INTEGER NOT NULL,
                        reason TEXT NOT NULL,
                        UNIQUE(started_at_ms, ended_at_ms, reason)
                    );

                    CREATE TABLE IF NOT EXISTS service_state (
                        key TEXT PRIMARY KEY,
                        value TEXT NOT NULL
                    );
                    """
                )

        await asyncio.to_thread(create)

    async def ping(self) -> bool:
        try:
            def ping_once() -> None:
                with closing(self._connect()) as connection:
                    connection.execute("SELECT 1").fetchone()

            await asyncio.to_thread(ping_once)
            return True
        except sqlite3.Error:
            return False

    async def insert_sales(self, sales: Iterable[EndedSale]) -> int:
        rows = list(sales)
        if not rows:
            return 0

        async with self._write_lock:
            def insert() -> int:
                inserted = 0
                now_ms = int(time.time() * 1000)
                with closing(self._connect()) as connection, connection:
                    for sale in rows:
                        cursor = connection.execute(
                            """
                            INSERT OR IGNORE INTO ended_sales(
                                auction_id, ended_at_ms, item_id, variant_key,
                                price, is_bin, parse_quality, ingested_at_ms
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                            (
                                sale.auction_id,
                                sale.ended_at_ms,
                                sale.item_id,
                                sale.variant_key,
                                sale.price,
                                int(sale.is_bin),
                                sale.parse_quality,
                                now_ms,
                            ),
                        )
                        inserted += cursor.rowcount
                return inserted

            return await asyncio.to_thread(insert)

    async def record_coverage_gap(self, started_at_ms: int, ended_at_ms: int, reason: str) -> None:
        if ended_at_ms <= started_at_ms:
            return
        async with self._write_lock:
            def insert() -> None:
                with closing(self._connect()) as connection, connection:
                    connection.execute(
                        """
                        INSERT OR IGNORE INTO coverage_gaps(started_at_ms, ended_at_ms, reason)
                        VALUES (?, ?, ?)
                        """,
                        (started_at_ms, ended_at_ms, reason[:120]),
                    )

            await asyncio.to_thread(insert)

    async def set_state(self, key: str, value: str) -> None:
        async with self._write_lock:
            def update() -> None:
                with closing(self._connect()) as connection, connection:
                    connection.execute(
                        """
                        INSERT INTO service_state(key, value) VALUES (?, ?)
                        ON CONFLICT(key) DO UPDATE SET value = excluded.value
                        """,
                        (key, value),
                    )

            await asyncio.to_thread(update)

    async def get_state(self, key: str) -> str | None:
        def select() -> str | None:
            with closing(self._connect()) as connection:
                row = connection.execute(
                    "SELECT value FROM service_state WHERE key = ?", (key,)
                ).fetchone()
                return str(row["value"]) if row is not None else None

        return await asyncio.to_thread(select)

    @staticmethod
    def _coverage_statuses(
        connection: sqlite3.Connection,
        *,
        now_ms: int,
        windows: Iterable[int],
        max_coverage_lag_seconds: int,
    ) -> dict[int, dict[str, Any]]:
        unique_windows = list(dict.fromkeys(int(value) for value in windows))
        if not unique_windows:
            return {}

        state_rows = connection.execute(
            """
            SELECT key, value FROM service_state
            WHERE key IN (?, ?)
            """,
            ("ended_history_started_wall_ms", "ended_last_success_wall_ms"),
        ).fetchall()
        state = {str(row["key"]): str(row["value"]) for row in state_rows}
        history_raw = state.get("ended_history_started_wall_ms")
        success_raw = state.get("ended_last_success_wall_ms")
        history_started = int(history_raw) if history_raw is not None else None
        last_success = int(success_raw) if success_raw is not None else None
        coverage_current = (
            last_success is not None
            and now_ms - last_success <= max_coverage_lag_seconds * 1000
        )

        cutoffs = {window: now_ms - window * 1000 for window in unique_windows}
        earliest_cutoff = min(cutoffs.values())
        gap_rows = connection.execute(
            """
            SELECT ended_at_ms FROM coverage_gaps
            WHERE ended_at_ms >= ?
            """,
            (earliest_cutoff,),
        ).fetchall()
        gap_ends = [int(row["ended_at_ms"]) for row in gap_rows]

        return {
            window: {
                "coverageComplete": (
                    not any(gap_end >= cutoffs[window] for gap_end in gap_ends)
                    and history_started is not None
                    and history_started <= cutoffs[window]
                    and coverage_current
                ),
                "coverageThrough": last_success,
                "windowSeconds": window,
            }
            for window in unique_windows
        }

    async def coverage_status(
        self,
        window_seconds: int,
        max_coverage_lag_seconds: int = 60,
    ) -> dict[str, Any]:
        window = int(window_seconds)
        now_ms = int(time.time() * 1000)

        def select() -> dict[str, Any]:
            with closing(self._connect()) as connection:
                connection.execute("BEGIN")
                return self._coverage_statuses(
                    connection,
                    now_ms=now_ms,
                    windows=(window,),
                    max_coverage_lag_seconds=max_coverage_lag_seconds,
                )[window]

        return await asyncio.to_thread(select)

    async def sale_statistics_batch(
        self,
        requests: Iterable[tuple[str, str | None]],
        *,
        window_seconds: Iterable[int],
        max_coverage_lag_seconds: int = 60,
    ) -> dict[tuple[str, str | None], dict[int, dict[str, Any]]]:
        unique_requests = list(dict.fromkeys(requests))
        windows = list(dict.fromkeys(int(value) for value in window_seconds))
        if not unique_requests or not windows:
            return {}
        now_ms = int(time.time() * 1000)

        def select() -> dict[tuple[str, str | None], dict[int, dict[str, Any]]]:
            with closing(self._connect()) as connection:
                connection.execute("BEGIN")
                coverage = self._coverage_statuses(
                    connection,
                    now_ms=now_ms,
                    windows=windows,
                    max_coverage_lag_seconds=max_coverage_lag_seconds,
                )
                result: dict[tuple[str, str | None], dict[int, dict[str, Any]]] = {
                    request: {} for request in unique_requests
                }
                wildcard_item_ids = {
                    item_id
                    for item_id, variant_key in unique_requests
                    if not variant_key
                }
                query_requests: list[tuple[str, str | None]] = []
                added_wildcards: set[str] = set()
                for item_id, variant_key in unique_requests:
                    if item_id in wildcard_item_ids:
                        if item_id not in added_wildcards:
                            query_requests.append((item_id, None))
                            added_wildcards.add(item_id)
                    else:
                        query_requests.append((item_id, variant_key))

                requested_values = ", ".join("(?, ?)" for _ in query_requests)
                request_parameters: list[Any] = []
                for item_id, variant_key in query_requests:
                    request_parameters.extend((item_id, variant_key))
                earliest_cutoff = min(now_ms - window * 1000 for window in windows)
                rows = connection.execute(
                    f"""
                    WITH requested(item_id, variant_key) AS (
                        VALUES {requested_values}
                    )
                    SELECT
                        sale.item_id,
                        sale.variant_key,
                        sale.ended_at_ms,
                        sale.price
                    FROM ended_sales AS sale
                    JOIN requested AS request
                      ON request.item_id = sale.item_id
                     AND (
                         request.variant_key IS NULL
                         OR request.variant_key = sale.variant_key
                     )
                    WHERE sale.ended_at_ms >= ?
                    """,
                    (*request_parameters, earliest_cutoff),
                )

                sales_by_request: dict[
                    tuple[str, str | None], list[tuple[int, float]]
                ] = {request: [] for request in unique_requests}
                exact_requests = {
                    request for request in unique_requests if request[1]
                }
                wildcard_requests: dict[str, list[tuple[str, str | None]]] = {}
                for request in unique_requests:
                    if not request[1]:
                        wildcard_requests.setdefault(request[0], []).append(request)

                for row in rows:
                    item_id = str(row["item_id"])
                    variant_key = str(row["variant_key"])
                    sale = (int(row["ended_at_ms"]), float(row["price"]))
                    exact_request = (item_id, variant_key)
                    if exact_request in exact_requests:
                        sales_by_request[exact_request].append(sale)
                    for wildcard_request in wildcard_requests.get(item_id, ()):
                        sales_by_request[wildcard_request].append(sale)

                for request in unique_requests:
                    request_sales = sales_by_request[request]
                    for window in windows:
                        cutoff_ms = now_ms - window * 1000
                        prices = [
                            price
                            for ended_at_ms, price in request_sales
                            if ended_at_ms >= cutoff_ms
                        ]
                        result[request][window] = {
                            "sampleCount": len(prices),
                            "median": float(statistics.median(prices)) if prices else None,
                            "minimum": min(prices) if prices else None,
                            "maximum": max(prices) if prices else None,
                            **coverage[window],
                        }
                return result

        return await asyncio.to_thread(select)

    async def sale_statistics(
        self,
        item_id: str,
        variant_key: str | None,
        *,
        window_seconds: int,
        max_coverage_lag_seconds: int = 60,
    ) -> dict[str, Any]:
        result = await self.sale_statistics_batch(
            [(item_id, variant_key)],
            window_seconds=[window_seconds],
            max_coverage_lag_seconds=max_coverage_lag_seconds,
        )
        return result[(item_id, variant_key)][window_seconds]

    async def prune_sales(self, retention_days: int) -> int:
        cutoff_ms = int((time.time() - retention_days * 86400) * 1000)
        async with self._write_lock:
            def delete() -> int:
                with closing(self._connect()) as connection, connection:
                    cursor = connection.execute(
                        "DELETE FROM ended_sales WHERE ended_at_ms < ?", (cutoff_ms,)
                    )
                    connection.execute(
                        "DELETE FROM coverage_gaps WHERE ended_at_ms < ?", (cutoff_ms,)
                    )
                    return cursor.rowcount

            return await asyncio.to_thread(delete)
