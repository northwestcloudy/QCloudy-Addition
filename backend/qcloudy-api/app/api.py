from __future__ import annotations

from typing import Annotated, Any, Literal

from fastapi import APIRouter, Path, Query, Request
from pydantic import BaseModel, Field, field_validator

from .errors import ApiProblem
from .responses import SCHEMA_VERSION, ok

router = APIRouter()


class PriceItemRequest(BaseModel):
    itemId: str = Field(min_length=1, max_length=128)
    variantKey: str | None = Field(default=None, min_length=1, max_length=128)

    @field_validator("itemId")
    @classmethod
    def normalize_item_id(cls, value: str) -> str:
        return value.strip().upper()


class BatchPriceRequest(BaseModel):
    items: list[PriceItemRequest] = Field(min_length=1, max_length=256)


def services(request: Request) -> Any:
    return request.app.state.services


@router.get("/health", tags=["Operations"])
async def health(request: Request) -> dict[str, Any]:
    return ok(request, {"status": "ok"})


@router.get("/ready", tags=["Operations"])
async def ready(request: Request) -> dict[str, Any]:
    svc = services(request)
    sqlite_ok = await svc.storage.ping()
    cache_health = await svc.cache.health()
    if not sqlite_ok:
        raise ApiProblem(503, "NOT_READY", "SQLite is unavailable.", retryable=True)
    if svc.settings.is_production and not (
        svc.settings.hypixel_api_key and svc.settings.hypixel_api_key.strip()
    ):
        raise ApiProblem(
            503,
            "NOT_READY",
            "The authenticated Hypixel integration is not configured.",
            retryable=False,
        )
    return ok(
        request,
        {
            "status": "ready",
            "dependencies": {"sqlite": "ok", **cache_health},
            "market": await svc.market.status(),
        },
    )


@router.get("/v1/dungeons/quick-view/{target}", tags=["Dungeons"])
async def dungeon_quick_view(
    request: Request,
    target: Annotated[str, Path(min_length=3, max_length=36)],
    floor: Annotated[str | None, Query(min_length=1, max_length=2)] = None,
) -> dict[str, Any]:
    svc = services(request)
    return {"schemaVersion": SCHEMA_VERSION, **await svc.dungeons.quick_view(target, floor)}


@router.post("/v1/market/prices", tags=["Market"])
async def prices(request: Request, body: BatchPriceRequest) -> dict[str, Any]:
    svc = services(request)
    values = await svc.market.prices(
        [(item.itemId, item.variantKey) for item in body.items]
    )
    return {
        "schemaVersion": SCHEMA_VERSION,
        "items": values,
        "source": "qca-market",
        "metadata": await svc.market.source_metadata(),
    }


@router.get("/v1/market/bazaar/shards", tags=["Market"])
async def shard_prices(
    request: Request,
    side: Annotated[Literal["instant_buy", "instant_sell"], Query()] = "instant_buy",
) -> dict[str, Any]:
    svc = services(request)
    snapshot, _ = await svc.market.bazaar_snapshot()
    field = "instantBuyPrice" if side == "instant_buy" else "instantSellPrice"
    prices = {
        item_id: product.get(field)
        for item_id, product in snapshot.get("products", {}).items()
        if item_id.startswith("SHARD_") and product.get(field) is not None
    }
    return {
        "schemaVersion": SCHEMA_VERSION,
        "source": "hypixel-bazaar",
        "metadata": svc.market.bazaar_source_metadata(snapshot),
        "side": side,
        "prices": prices,
    }


@router.get("/v1/market/status", tags=["Market"])
async def market_status(request: Request) -> dict[str, Any]:
    return ok(request, await services(request).market.status(), source="qca-market")
