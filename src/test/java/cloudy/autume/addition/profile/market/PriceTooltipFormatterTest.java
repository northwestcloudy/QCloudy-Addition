package cloudy.autume.addition.profile.market;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class PriceTooltipFormatterTest {
    @Test
    void auctionRowsUseExactOrderFullNumbersAndPixelColumns() {
        EnumMap<MarketQuoteKind, MarketQuote> quotes = new EnumMap<>(MarketQuoteKind.class);
        quotes.put(MarketQuoteKind.NPC_SELL_PRICE, quote("10", "20"));
        quotes.put(MarketQuoteKind.CLEAN_LOW_BIN_PRICE,
                quote("1234567.89", "2469135.78"));
        quotes.put(MarketQuoteKind.THREE_DAY_AVERAGE_PRICE,
                unavailable());
        quotes.put(MarketQuoteKind.ITEM_NET_WORTH_VALUE,
                quote("1200000", "2400000"));
        MarketTooltipPrice price = new MarketTooltipPrice("slot",
                new MarketTooltipQuery("HYPERION", null, 2), MarketType.AUCTION, quotes);

        PriceTooltipLayout layout = PriceTooltipFormatter.format(price,
                text -> text.length() * 2, 6);

        assertEquals(List.of("NPC Sell Price:", "Low. BIN Price:", "Item NW Value :"),
                layout.rows().stream().map(PriceTooltipRow::label).toList());
        assertEquals(List.of("20 Coins (10 each)",
                        "2,469,135.78 Coins (1,234,567.89 each)",
                        "2,400,000 Coins (1,200,000 each)"),
                layout.rows().stream().map(PriceTooltipRow::value).toList());
        assertEquals("Item NW Value :".length() * 2, layout.labelWidth());
        assertEquals(layout.labelWidth() + 6, layout.valueColumnX());
        assertFalse(layout.rows().getFirst().label().endsWith("  "));
    }

    @Test
    void bazaarRowsUseExactOrderAndSingleQuantityHasNoParentheses() {
        Map<MarketQuoteKind, MarketQuote> quotes = Map.of(
                MarketQuoteKind.BAZAAR_SELL_PRICE, quote("1265.189", "1265.189"),
                MarketQuoteKind.BAZAAR_BUY_PRICE, quote("1344.52", "1344.52"));
        MarketTooltipPrice price = new MarketTooltipPrice("slot",
                new MarketTooltipQuery("ENCHANTED_DIAMOND", null, 1),
                MarketType.BAZAAR, quotes);

        PriceTooltipLayout layout = PriceTooltipFormatter.format(price, String::length);

        assertEquals(List.of("BZ Sell Price:", "BZ Buy Price:"),
                layout.rows().stream().map(PriceTooltipRow::label).toList());
        assertEquals(List.of("1,265.19 Coins", "1,344.52 Coins"),
                layout.rows().stream().map(PriceTooltipRow::value).toList());
    }

    private static MarketQuote quote(String unit, String total) {
        return new MarketQuote("available", new BigDecimal(unit), new BigDecimal(total),
                null, null, false, null);
    }

    private static MarketQuote unavailable() {
        return new MarketQuote("not_found", null, null, null, null, false, null);
    }
}
