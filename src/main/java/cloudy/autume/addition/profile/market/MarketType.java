package cloudy.autume.addition.profile.market;

/** Market family selected by the QCA price service. */
public enum MarketType {
    BAZAAR("bazaar"),
    AUCTION("auction"),
    NONE("none");

    private final String wireName;

    MarketType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    static MarketType parse(String value) {
        for (MarketType type : values()) {
            if (type.wireName.equals(value)) return type;
        }
        throw new IllegalArgumentException("Unknown marketType");
    }
}
