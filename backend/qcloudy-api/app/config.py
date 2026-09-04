from __future__ import annotations

from pathlib import Path

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Runtime configuration loaded exclusively from environment variables."""

    model_config = SettingsConfigDict(
        env_prefix="QCA_",
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    environment: str = "development"

    hypixel_api_key: str | None = Field(default=None, repr=False)
    hypixel_base_url: str = "https://api.hypixel.net/v2"
    mojang_base_url: str = "https://api.mojang.com"
    upstream_timeout_seconds: float = 12.0
    upstream_connect_timeout_seconds: float = 5.0
    upstream_max_connections: int = 16
    hypixel_authenticated_budget_per_minute: int = Field(default=100, ge=1)
    hypixel_authenticated_burst: int = Field(default=10, ge=1)
    hypixel_authenticated_429_backoff_seconds: int = Field(default=60, ge=1, le=3600)

    redis_url: str | None = None
    redis_prefix: str = "qca:v1:"
    cache_memory_max_entries: int = Field(default=4096, ge=128)
    cache_memory_max_bytes: int = Field(default=128 * 1024 * 1024, ge=1024 * 1024)
    sqlite_path: Path = Path("data/qcloudy-api.sqlite3")

    scheduler_enabled: bool = True
    bazaar_interval_seconds: int = 60
    auction_interval_seconds: int = 120
    ended_interval_seconds: int = 30
    auction_page_concurrency: int = 6

    name_fresh_seconds: int = 72 * 60 * 60
    name_stale_seconds: int = 72 * 60 * 60
    name_negative_seconds: int = 15 * 60
    dungeon_player_fresh_seconds: int = 2 * 60
    dungeon_player_stale_seconds: int = 10 * 60
    dungeon_profiles_fresh_seconds: int = 2 * 60
    dungeon_profiles_stale_seconds: int = 10 * 60

    bazaar_stale_seconds: int = 10 * 60
    auction_stale_seconds: int = 15 * 60
    ended_gap_threshold_seconds: int = 60
    ended_sales_retention_days: int = 30
    cors_origins: list[str] = Field(default_factory=list)

    @property
    def is_production(self) -> bool:
        return self.environment.lower() == "production"
