package cloudy.autume.addition.profile.market;

import cloudy.autume.addition.profile.ProfileException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MarketTooltipPriceJsonParserTest {
    @Test
    void parsesFrozenEchoAndKeepsUnavailableQuotesHidden() {
        List<MarketTooltipPrice> prices = MarketTooltipPriceJsonParser.parse(response(
                "slot-1", "HYPERION", "upgrade=10", 2, "auction",
                "\"npcSellPrice\":{\"status\":\"available\",\"unitCoins\":10,"
                        + "\"totalCoins\":20},"
                        + "\"cleanLowBinPrice\":{\"status\":\"stale\","
                        + "\"unitCoins\":470000000,\"totalCoins\":940000000,"
                        + "\"stale\":true,\"listingCount\":7},"
                        + "\"threeDayAveragePrice\":{\"status\":\"not_found\","
                        + "\"unitCoins\":null,\"totalCoins\":null},"
                        + "\"itemNetWorthValue\":null,"
                        + unavailable("bazaarSellPrice") + ","
                        + unavailable("bazaarBuyPrice")));

        MarketTooltipPrice price = prices.getFirst();
        assertEquals(new MarketTooltipQuery("HYPERION", "upgrade=10", 2), price.query());
        assertEquals(MarketType.AUCTION, price.marketType());
        assertEquals(new BigDecimal("470000000"), price.quote(
                MarketQuoteKind.CLEAN_LOW_BIN_PRICE).orElseThrow().unitCoins());
        assertTrue(price.quote(MarketQuoteKind.CLEAN_LOW_BIN_PRICE).orElseThrow().stale());
        assertFalse(price.quote(MarketQuoteKind.THREE_DAY_AVERAGE_PRICE)
                .orElseThrow().displayable());
        assertTrue(price.quote(MarketQuoteKind.ITEM_NET_WORTH_VALUE).isEmpty());
    }

    @Test
    void rejectsUnsupportedSchemasZerosUnknownQuoteKeysAndDuplicateIds() {
        ProfileException schema = assertThrows(ProfileException.class,
                () -> MarketTooltipPriceJsonParser.parse(
                        "{\"schemaVersion\":2,\"items\":[]}"));
        assertEquals(ProfileException.Code.UNSUPPORTED_SCHEMA, schema.code());

        String zero = response("slot", "A", null, 1, "auction",
                "\"npcSellPrice\":{\"status\":\"available\",\"unitCoins\":0,"
                        + "\"totalCoins\":0},"
                        + unavailable("cleanLowBinPrice") + ","
                        + unavailable("threeDayAveragePrice") + ","
                        + unavailable("itemNetWorthValue") + ","
                        + unavailable("bazaarSellPrice") + ","
                        + unavailable("bazaarBuyPrice"));
        assertEquals(ProfileException.Code.INVALID_RESPONSE,
                assertThrows(ProfileException.class,
                        () -> MarketTooltipPriceJsonParser.parse(zero)).code());

        String unknown = zero.replace("\"bazaarBuyPrice\"",
                "\"unexpectedPrice\"");
        assertEquals(ProfileException.Code.INVALID_RESPONSE,
                assertThrows(ProfileException.class,
                        () -> MarketTooltipPriceJsonParser.parse(unknown)).code());

        String item = item("same", "A", null, 1, "none", allUnavailableQuotes());
        String duplicate = "{\"schemaVersion\":1,\"items\":[" + item + "," + item + "]}";
        assertEquals(ProfileException.Code.INVALID_RESPONSE,
                assertThrows(ProfileException.class,
                        () -> MarketTooltipPriceJsonParser.parse(duplicate)).code());
    }

    static String response(String requestId, String itemId, String variantKey,
                           int quantity, String marketType, String quoteMembers) {
        return "{\"schemaVersion\":1,\"currency\":\"SKYBLOCK_COINS\","
                + "\"items\":[" + item(requestId, itemId, variantKey, quantity,
                marketType, quoteMembers) + "],\"source\":\"qca-market\","
                + "\"metadata\":{}}";
    }

    static String item(String requestId, String itemId, String variantKey,
                       int quantity, String marketType, String quoteMembers) {
        return "{\"requestId\":\"" + requestId + "\",\"itemId\":\"" + itemId
                + "\"," + (variantKey == null ? "\"variantKey\":null,"
                : "\"variantKey\":\"" + variantKey + "\",")
                + "\"quantity\":" + quantity + ",\"marketType\":\"" + marketType
                + "\",\"quotes\":{" + quoteMembers + "}}";
    }

    static String allUnavailableQuotes() {
        return unavailable("npcSellPrice") + ","
                + unavailable("cleanLowBinPrice") + ","
                + unavailable("threeDayAveragePrice") + ","
                + unavailable("itemNetWorthValue") + ","
                + unavailable("bazaarSellPrice") + ","
                + unavailable("bazaarBuyPrice");
    }

    private static String unavailable(String name) {
        return "\"" + name + "\":{\"status\":\"not_applicable\","
                + "\"unitCoins\":null,\"totalCoins\":null}";
    }
}
