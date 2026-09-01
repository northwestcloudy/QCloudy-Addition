package cloudy.autume.addition.profile.market;

/** Frozen quote keys returned by POST /v1/market/tooltip-prices. */
public enum MarketQuoteKind {
    NPC_SELL_PRICE("npcSellPrice"),
    CLEAN_LOW_BIN_PRICE("cleanLowBinPrice"),
    THREE_DAY_AVERAGE_PRICE("threeDayAveragePrice"),
    ITEM_NET_WORTH_VALUE("itemNetWorthValue"),
    BAZAAR_SELL_PRICE("bazaarSellPrice"),
    BAZAAR_BUY_PRICE("bazaarBuyPrice");

    private final String wireName;

    MarketQuoteKind(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    static MarketQuoteKind parse(String value) {
        for (MarketQuoteKind kind : values()) {
            if (kind.wireName.equals(value)) return kind;
        }
        throw new IllegalArgumentException("Unknown market quote key");
    }
}
