# QCloudy profile and market privacy

This document covers QCA's optional read-only Player Profile Viewer and market-price requests. It does not replace the general client/rules boundary in `COMPLIANCE.md`.

## When a request happens

QCA requests profile data only after the player opens `//pv` or `/qpv`, changes the selected SkyBlock profile, retries a failed view, or opens a supplementary section that is not available in the current bounded cache. The Shard Planner requests its bounded price snapshot when its price data is loaded. Neither path runs a gameplay automation, sends a Minecraft chat/server command, or starts a market collector.

## Data sent by the mod

The destination is fixed to `https://api.qcloudy.net`. The server and normal network infrastructure can observe the connecting IP address and QCA User-Agent. A profile request also contains the explicitly requested Minecraft player name or UUID and, when selected, the SkyBlock profile ID. The mod does not send the Minecraft session token, Microsoft credentials, Hypixel API key, current server address, mod list, chat history, coordinates, inventory upload, cookies, or a telemetry/device identifier.

## Processing and retention

The QCloudy service resolves names and retrieves publicly available or owner-enabled Hypixel data using a server-side application key. It transforms and bounds the result before returning it to the mod. Name/UUID data is cached for 72 hours with no longer stale extension; invalid names use a 15-minute negative cache. Player and complete SkyBlock-profile responses are cached for one hour with technical stale fallback capped at 24 hours. Museum is cached for six hours and Garden for twelve hours, both with a 24-hour technical stale ceiling. A successful private/missing response replaces older data.

Bazaar snapshots refresh about every 60 seconds and are retained as a usable stale fallback for at most ten minutes. Active-auction snapshots refresh about every two minutes and are stale-expired after fifteen minutes. Ended-auction sale samples are deduplicated and retained for approximately 30 days to calculate aggregate medians and coverage; they are market transactions, not QCA user lookup history. Public Hypixel item-resource metadata is refreshed every six hours and may remain in a 14-day technical cache; it is static item metadata and contains no QCA player lookup event.

QCA does not store a PV access/request history, player-session history, or cross-session analytics profile. Shared name/profile caches are keyed by the requested data identity and freshness window, not by the QCA user who requested it. The production templates disable both Nginx and Uvicorn access logs so player names and UUIDs in request paths are not retained as a browsing history.

The playable mod caches a successful response in memory for at most ten minutes and never beyond server-provided freshness metadata. It does not persist remote player snapshots or market history into `config/qcloudy_addition.json`.

## Security boundary

The Hypixel application key remains in the server environment and is never included in the JAR or returned to a client. The public service exposes fixed transformed routes, not an arbitrary Hypixel proxy. The mod requires HTTPS, rejects redirects and origin changes, and bounds request time and response size. Unknown prices remain unknown and private/missing data is explicitly labelled. Operational error logs may retain bounded technical status information, but must not contain a PV request path, player query, API key, request body, or complete upstream profile response.
