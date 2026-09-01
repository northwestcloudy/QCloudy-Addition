from __future__ import annotations

from dataclasses import dataclass


@dataclass(slots=True)
class ApiProblem(Exception):
    status_code: int
    code: str
    message: str
    retryable: bool = False
    retry_after_seconds: int | None = None

    def __str__(self) -> str:
        return self.message


class UpstreamTemporaryError(ApiProblem):
    def __init__(self, message: str, retry_after_seconds: int | None = None):
        super().__init__(
            status_code=503,
            code="UPSTREAM_TEMPORARY_FAILURE",
            message=message,
            retryable=True,
            retry_after_seconds=retry_after_seconds,
        )


class UpstreamAuthenticationError(ApiProblem):
    def __init__(self, message: str = "The upstream API credentials were rejected."):
        super().__init__(
            status_code=503,
            code="UPSTREAM_AUTHENTICATION_FAILURE",
            message=message,
            retryable=False,
        )


class UpstreamNotFound(ApiProblem):
    def __init__(self, message: str = "The requested upstream resource was not found."):
        super().__init__(404, "UPSTREAM_NOT_FOUND", message, retryable=False)


class SnapshotConsistencyError(UpstreamTemporaryError):
    def __init__(self, message: str = "Auction pages did not belong to one snapshot."):
        super().__init__(message)
        self.code = "AUCTION_SNAPSHOT_INCONSISTENT"
