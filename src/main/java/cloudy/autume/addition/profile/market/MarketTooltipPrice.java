package cloudy.autume.addition.profile.market;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Validated response item for a single PV tooltip query. */
public record MarketTooltipPrice(String requestId,
                                 MarketTooltipQuery query,
                                 MarketType marketType,
                                 Map<MarketQuoteKind, MarketQuote> quotes) {
    public MarketTooltipPrice {
        requestId = new MarketTooltipRequestItem(requestId, query).requestId();
        query = Objects.requireNonNull(query, "query");
        marketType = Objects.requireNonNull(marketType, "marketType");
        if (quotes == null) throw new IllegalArgumentException("Missing quotes");
        EnumMap<MarketQuoteKind, MarketQuote> copy = new EnumMap<>(MarketQuoteKind.class);
        for (Map.Entry<MarketQuoteKind, MarketQuote> entry : quotes.entrySet()) {
            copy.put(Objects.requireNonNull(entry.getKey(), "quote kind"), entry.getValue());
        }
        quotes = Collections.unmodifiableMap(copy);
    }

    public Optional<MarketQuote> quote(MarketQuoteKind kind) {
        return Optional.ofNullable(quotes.get(Objects.requireNonNull(kind, "kind")));
    }
}
