package cloudy.autume.addition.profile.market;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Ordered prices returned to one PV hover/batch caller. */
public record MarketTooltipPriceBatch(List<MarketTooltipPrice> items,
                                      boolean entirelyFromCache) {
    public MarketTooltipPriceBatch {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
    }

    public Optional<MarketTooltipPrice> price(MarketTooltipQuery query) {
        Objects.requireNonNull(query, "query");
        return items.stream().filter(item -> item.query().equals(query)).findFirst();
    }

    public Map<MarketTooltipQuery, MarketTooltipPrice> byQuery() {
        LinkedHashMap<MarketTooltipQuery, MarketTooltipPrice> result = new LinkedHashMap<>();
        for (MarketTooltipPrice item : items) result.put(item.query(), item);
        return Map.copyOf(result);
    }
}
