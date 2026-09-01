from __future__ import annotations

import sys
from pathlib import Path
from typing import Any

import httpx
import pytest
import pytest_asyncio

PROJECT = Path(__file__).resolve().parents[1]
if str(PROJECT) not in sys.path:
    sys.path.insert(0, str(PROJECT))

from app.cache import CacheStore
from app.config import Settings
from app.main import create_app
from app.storage import MarketStorage


PLAYER_UUID = "a" * 32
PROFILE_ID = "b" * 32


class FakeUpstream:
    def __init__(self) -> None:
        self.calls: dict[str, int] = {}
        self.resolve_payload: dict[str, Any] | None = {
            "uuid": PLAYER_UUID,
            "name": "NorthwestCloudy",
        }
        self.player_payload = {
            "success": True,
            "player": {"uuid": PLAYER_UUID, "displayname": "NorthwestCloudy"},
        }
        self.profiles_payload = {
            "success": True,
            "profiles": [
                {
                    "profile_id": PROFILE_ID,
                    "cute_name": "Apple",
                    "selected": True,
                    "members": {
                        PLAYER_UUID: {
                            "last_save": 100,
                            "inventory": {},
                            "pets_data": {"pets": []},
                        }
                    },
                }
            ],
        }
        self.bazaar_payload: dict[str, Any] = {
            "success": True,
            "lastUpdated": 1000,
            "products": {
                "SHARD_TEST": {
                    "product_id": "SHARD_TEST",
                    "quick_status": {"buyPrice": 125.5, "sellPrice": 100.25},
                }
            },
        }
        self.auction_pages: dict[int, dict[str, Any]] = {
            0: {
                "success": True,
                "page": 0,
                "totalPages": 2,
                "totalAuctions": 2,
                "lastUpdated": 2000,
                "auctions": [
                    {
                        "uuid": "auction-0",
                        "bin": True,
                        "starting_bid": 1000,
                        "item_name": "Test Blade",
                        "tier": "RARE",
                    }
                ],
            },
            1: {
                "success": True,
                "page": 1,
                "totalPages": 2,
                "totalAuctions": 2,
                "lastUpdated": 2000,
                "auctions": [
                    {
                        "uuid": "auction-1",
                        "bin": True,
                        "starting_bid": 1200,
                        "item_name": "Test Blade",
                        "tier": "RARE",
                    }
                ],
            },
        }
        self.ended_payload: dict[str, Any] = {
            "success": True,
            "lastUpdated": 3000,
            "auctions": [
                {
                    "auction_id": "ended-1",
                    "buyer": "c" * 32,
                    "price": 1100,
                    "timestamp": 1_900_000_000_000,
                    "bin": True,
                    "item_name": "Test Blade",
                    "tier": "RARE",
                }
            ],
        }
        self.museum_payload: dict[str, Any] = {
            "success": True,
            "members": {PLAYER_UUID: {"items": {}}},
        }
        self.garden_payload: dict[str, Any] = {
            "success": True,
            "garden": {"uuid": PROFILE_ID},
        }

    def _called(self, name: str) -> None:
        self.calls[name] = self.calls.get(name, 0) + 1

    async def resolve_name(self, name: str) -> dict[str, Any] | None:
        self._called("resolve")
        return self.resolve_payload

    async def fetch_player(self, player_uuid: str) -> dict[str, Any]:
        self._called("player")
        return self.player_payload

    async def fetch_profiles(self, player_uuid: str) -> dict[str, Any]:
        self._called("profiles")
        return self.profiles_payload

    async def fetch_museum(self, profile_id: str) -> dict[str, Any]:
        self._called("museum")
        return self.museum_payload

    async def fetch_garden(self, profile_id: str) -> dict[str, Any]:
        self._called("garden")
        return self.garden_payload

    async def fetch_bazaar(self) -> dict[str, Any]:
        self._called("bazaar")
        return self.bazaar_payload

    async def fetch_auction_page(self, page: int) -> dict[str, Any]:
        self._called(f"auction:{page}")
        return self.auction_pages[page]

    async def fetch_ended_auctions(self) -> dict[str, Any]:
        self._called("ended")
        return self.ended_payload

    async def fetch_item_resources(self) -> dict[str, Any]:
        self._called("items")
        return {"success": True, "lastUpdated": 1, "items": []}

    async def close(self) -> None:
        return None


@pytest.fixture
def settings(tmp_path: Path) -> Settings:
    return Settings(
        scheduler_enabled=False,
        hypixel_api_key="test-secret",
        sqlite_path=tmp_path / "qca.sqlite3",
        redis_url=None,
    )


@pytest.fixture
def upstream() -> FakeUpstream:
    return FakeUpstream()


@pytest_asyncio.fixture
async def app_client(settings: Settings, upstream: FakeUpstream):
    cache = CacheStore(prefix="test:")
    storage = MarketStorage(settings.sqlite_path)
    app = create_app(settings, upstream=upstream, cache=cache, storage=storage)
    async with app.router.lifespan_context(app):
        transport = httpx.ASGITransport(app=app, raise_app_exceptions=False)
        async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
            yield app, client
