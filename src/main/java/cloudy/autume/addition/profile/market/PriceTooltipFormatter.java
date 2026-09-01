package cloudy.autume.addition.profile.market;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.ToIntFunction;

/** QCA PV price-row ordering, coin formatting, and pixel-column calculation. */
public final class PriceTooltipFormatter {
    public static final int DEFAULT_COLUMN_GAP = 8;
    private static final List<RowDefinition> AUCTION_ROWS = List.of(
            new RowDefinition(MarketQuoteKind.NPC_SELL_PRICE, "NPC Sell Price:"),
            new RowDefinition(MarketQuoteKind.CLEAN_LOW_BIN_PRICE, "Low. BIN Price:"),
            new RowDefinition(MarketQuoteKind.THREE_DAY_AVERAGE_PRICE, "3 Day Avg. Price:"),
            new RowDefinition(MarketQuoteKind.ITEM_NET_WORTH_VALUE, "Item NW Value :"));
    private static final List<RowDefinition> BAZAAR_ROWS = List.of(
            new RowDefinition(MarketQuoteKind.NPC_SELL_PRICE, "NPC Sell Price:"),
            new RowDefinition(MarketQuoteKind.BAZAAR_SELL_PRICE, "BZ Sell Price:"),
            new RowDefinition(MarketQuoteKind.BAZAAR_BUY_PRICE, "BZ Buy Price:"));
    private static final List<RowDefinition> NONE_ROWS = List.of(
            new RowDefinition(MarketQuoteKind.NPC_SELL_PRICE, "NPC Sell Price:"));

    private PriceTooltipFormatter() {
    }

    public static PriceTooltipLayout format(MarketTooltipPrice price,
                                            ToIntFunction<String> textWidth) {
        return format(price, textWidth, DEFAULT_COLUMN_GAP);
    }

    public static PriceTooltipLayout format(MarketTooltipPrice price,
                                            ToIntFunction<String> textWidth,
                                            int columnGap) {
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(textWidth, "textWidth");
        if (columnGap < 0) throw new IllegalArgumentException("Negative column gap");
        List<RowDefinition> definitions = switch (price.marketType()) {
            case AUCTION -> AUCTION_ROWS;
            case BAZAAR -> BAZAAR_ROWS;
            case NONE -> NONE_ROWS;
        };
        List<PriceTooltipRow> rows = new ArrayList<>();
        for (RowDefinition definition : definitions) {
            price.quote(definition.kind()).filter(MarketQuote::displayable).ifPresent(quote ->
                    rows.add(new PriceTooltipRow(definition.kind(), definition.label(),
                            value(quote, price.query().quantity()), quote.stale())));
        }
        int labelWidth = 0;
        int valueWidth = 0;
        for (PriceTooltipRow row : rows) {
            labelWidth = Math.max(labelWidth, safeWidth(textWidth, row.label()));
            valueWidth = Math.max(valueWidth, safeWidth(textWidth, row.value()));
        }
        int valueColumn = rows.isEmpty() ? 0 : Math.addExact(labelWidth, columnGap);
        int totalWidth = rows.isEmpty() ? 0 : Math.addExact(valueColumn, valueWidth);
        return new PriceTooltipLayout(rows, labelWidth, valueColumn, totalWidth);
    }

    public static String value(MarketQuote quote, int quantity) {
        Objects.requireNonNull(quote, "quote");
        if (!quote.displayable()) throw new IllegalArgumentException("Quote is not displayable");
        if (quantity < 1) throw new IllegalArgumentException("quantity must be positive");
        String total = coins(quote.totalCoins()) + " Coins";
        if (quantity == 1) return total;
        return total + " (" + coins(quote.unitCoins()) + " each)";
    }

    public static String coins(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException("Price must be positive");
        }
        DecimalFormat format = new DecimalFormat("#,##0.##",
                DecimalFormatSymbols.getInstance(Locale.US));
        format.setRoundingMode(RoundingMode.HALF_UP);
        format.setGroupingUsed(true);
        return format.format(value);
    }

    private static int safeWidth(ToIntFunction<String> textWidth, String text) {
        int width = textWidth.applyAsInt(text);
        if (width < 0) throw new IllegalArgumentException("Negative text width");
        return width;
    }

    private record RowDefinition(MarketQuoteKind kind, String label) {
    }
}
