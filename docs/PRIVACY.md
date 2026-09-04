# QCloudy Dungeon quick-view and market privacy

This document covers QCA's Dungeon Player Quick View and Shard Planner market requests. It does not replace the general client/rules boundary in `COMPLIANCE.md`.

## When a request happens

QCA requests Dungeon data only after the client receives the exact Dungeon Finder message that a new player joined the dungeon group, the feature is enabled, and the session is on Hypixel. It queries that newcomer only. It does not browse Party Finder listings, poll the party, or create player history. The Shard Planner requests its bounded price snapshot when its price data is explicitly loaded.

## Data sent by the mod

The destination is fixed to `https://api.qcloudy.net`. The request contains the joining Minecraft player name and, when the local scoreboard exposes one, the queued floor ID. The server and ordinary network infrastructure can observe the connecting IP address and QCA User-Agent. The mod does not send a Minecraft session token, Microsoft credentials, Hypixel API key, current server address, mod list, chat history, coordinates, inventory upload, cookie, or telemetry/device identifier.

Clicking `CLICK HERE TO KICK THE PLAYER OUT` sends `/party kick <validated player>` to the Minecraft server through a normal chat click event. No request result can trigger that command automatically.

## Processing and retention

The QCloudy service resolves the joining name and fetches the corresponding Hypixel player and SkyBlock Profiles with a server-side application key. It selects the active profile (or latest visible profile), projects only the fields needed for the chat card, and returns a bounded response. Name/UUID data is cached for 72 hours with a 15-minute negative cache. Dungeon player and Profile data are fresh for two minutes; a prior value may be used for at most ten minutes only after a technical upstream failure. Private or missing results remain explicit and are not converted to zero.

The playable mod coalesces identical in-flight requests and keeps successful quick-view results in process memory for 60 seconds. It cancels pending work when the client session changes and does not write remote player snapshots into `config/qcloudy_addition.json`.

Bazaar snapshots refresh about every 60 seconds and have a ten-minute technical stale limit. Active-auction snapshots refresh about every two minutes and have a fifteen-minute stale limit. Deduplicated ended-auction market samples are retained for about 30 days; they are market transactions, not QCA player lookup history. SQLite stores those market samples only, not Dungeon quick views. Production templates disable Nginx and Uvicorn access logs so names in request paths are not retained as browsing history.

## Security boundary

The Hypixel application key remains in the server environment and is never packaged in the JAR or returned to a client. The service exposes fixed transformed routes, not an arbitrary Hypixel proxy. The mod requires HTTPS, rejects redirects and origin changes, and bounds request time and response size. Operational logs may contain bounded technical status but must not contain the player query, API key, request body, or complete upstream response.
