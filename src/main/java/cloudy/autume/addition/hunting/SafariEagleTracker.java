package cloudy.autume.addition.hunting;

/** Session-only aggregator for evidence about the local player's Safari Eagle. */
public final class SafariEagleTracker {
    private static SafariEagleState current = SafariEagleState.EMPTY;

    private SafariEagleTracker() {
    }

    public static void observeSafariEssenceShop(Iterable<String> boundedItemText) {
        current = current.merge(SafariEagleParser.safariEssenceShop(boundedItemText));
    }

    public static void observeSafariWidget(Iterable<String> boundedWidgetText) {
        current = current.merge(SafariEagleParser.safariWidget(boundedWidgetText));
    }

    public static void observeOwnerBoundEntity(String receivedName, boolean ownerBound) {
        current = current.merge(SafariEagleParser.ownerBoundEntity(receivedName, ownerBound));
    }

    public static SafariEagleState current() {
        return current;
    }

    /** Must be called on world/profile change and whenever the player exits Safari. */
    public static void reset() {
        current = SafariEagleState.EMPTY;
    }
}
