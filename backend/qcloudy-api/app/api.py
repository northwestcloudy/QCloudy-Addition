from __future__ import annotations

from typing import Annotated, Any, Literal

from fastapi import APIRouter, Path, Query, Request
from pydantic import BaseModel, Field, field_validator

from .errors import ApiProblem
from .market import empty_source
from .profile_service import normalize_uuid
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


@router.get("/v1/pv/{target}", tags=["Player Viewer"])
async def player_viewer(
    request: Request,
    target: Annotated[str, Path(min_length=3, max_length=36)],
    profile_id: Annotated[
        str | None, Query(alias="profileId", min_length=32, max_length=36)
    ] = None,
) -> dict[str, Any]:
    svc = services(request)
    if profile_id is not None:
        profile_id = normalize_uuid(profile_id)
    data = await svc.profiles.pv(target, profile_id)
    profile_projection_complete = bool(data.pop("_profileProjectionComplete", False))
    data["sources"].update({"museum": empty_source(), "garden": empty_source()})
    market_payload, market_source = await svc.market.portfolio(
        data["sections"],
        holdings_projection_complete=profile_projection_complete,
    )
    data["sources"]["market"] = market_source
    market_status = market_source["status"]
    if market_status == "error":
        section_status = "not_loaded"
        message = "Market snapshots are not ready yet."
    elif market_status == "stale":
        section_status = "stale"
        message = "Market valuation uses a stale published snapshot."
    else:
        section_status = "available"
        message = (
            "The displayed value is a partial estimate because some items are unpriced or omitted."
            if not market_payload.get("estimateComplete", False)
            else None
        )
    data["sections"]["market"] = {
        "status": section_status,
        "message": message,
        "payload": market_payload,
    }
    if market_status in {"stale", "error"} or not market_payload.get(
        "estimateComplete", False
    ):
        data["partial"] = True
    return {"schemaVersion": SCHEMA_VERSION, **data}


@router.get("/v1/pv/{player_uuid}/{profile_id}/museum", tags=["Player Viewer"])
async def museum(
    request: Request,
    player_uuid: Annotated[str, Path(min_length=32, max_length=36)],
    profile_id: Annotated[str, Path(min_length=32, max_length=36)],
) -> dict[str, Any]:
    svc = services(request)
    normalized_uuid = normalize_uuid(player_uuid)
    result = await svc.profiles.museum(normalized_uuid, profile_id)
    source = result.metadata.to_source()
    if result.value is None:
        source["status"] = "private"
    return {
        "schemaVersion": SCHEMA_VERSION,
        "identity": {"uuid": normalized_uuid},
        "profileId": normalize_uuid(profile_id),
        "sections": {
            "museum": {
                "status": "private" if result.value is None else "available",
                "message": (
                    "Museum data is private or unavailable for this member."
                    if result.value is None
                    else None
                ),
                "payload": result.value or {},
            }
        },
        "sources": {"museum": source},
    }


@router.get("/v1/pv/{player_uuid}/{profile_id}/garden", tags=["Player Viewer"])
async def garden(
    request: Request,
    player_uuid: Annotated[str, Path(min_length=32, max_length=36)],
    profile_id: Annotated[str, Path(min_length=32, max_length=36)],
) -> dict[str, Any]:
    svc = services(request)
    normalized_uuid = normalize_uuid(player_uuid)
    result = await svc.profiles.garden(normalized_uuid, profile_id)
    status = "available" if result.value is not None else "not_found"
    source = result.metadata.to_source()
    if result.value is None:
        source["status"] = "not_found"
    return {
        "schemaVersion": SCHEMA_VERSION,
        "identity": {"uuid": normalized_uuid},
        "profileId": normalize_uuid(profile_id),
        "sections": {
            "garden": {
                "status": status,
                "message": "This profile has no Garden data." if result.value is None else None,
                "payload": result.value or {},
            }
        },
        "sources": {"garden": source},
    }


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
        "metadata": await svc.market.profile_source(),
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
