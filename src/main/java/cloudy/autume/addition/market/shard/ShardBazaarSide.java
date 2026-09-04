package cloudy.autume.addition.market.shard;

/** Requested side of the QCloudy Shard-only Bazaar snapshot. */
public enum ShardBazaarSide {
    INSTANT_BUY("instant_buy"),
    INSTANT_SELL("instant_sell");

    private final String wireName;

    ShardBazaarSide(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    static ShardBazaarSide parse(String value) {
        for (ShardBazaarSide side : values()) {
            if (side.wireName.equals(value)) return side;
        }
        throw new IllegalArgumentException("Unknown Shard Bazaar side: " + value);
    }
}
