package cloudy.autume.addition.profile;

/** Successful Shard Bazaar snapshot and whether it came from the session cache. */
public record ShardBazaarLoadResult(ShardBazaarSnapshot snapshot, boolean fromSessionCache) {
    public ShardBazaarLoadResult {
        snapshot = java.util.Objects.requireNonNull(snapshot, "snapshot");
    }
}
