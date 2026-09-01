package cloudy.autume.addition.profile.market;

import java.util.Objects;

/** Wire request entry for POST /v1/market/tooltip-prices. */
public record MarketTooltipRequestItem(String requestId, MarketTooltipQuery query) {
    public static final int MAX_REQUEST_ID_LENGTH = 64;

    public MarketTooltipRequestItem {
        if (requestId == null) throw new IllegalArgumentException("Missing requestId");
        requestId = requestId.trim();
        if (requestId.isEmpty() || requestId.length() > MAX_REQUEST_ID_LENGTH
                || !requestId.matches("[A-Za-z0-9._:-]+")) {
            throw new IllegalArgumentException("Invalid requestId");
        }
        query = Objects.requireNonNull(query, "query");
    }
}
