package cloudy.autume.addition.profile.ui;

/** Pure geometry for the responsive profile viewer. */
public final class ProfileViewerLayout {
    static final int HEADER_HEIGHT = 32;
    static final int IDENTITY_HEIGHT = 48;
    static final int OUTER_MARGIN = 10;
    static final int INNER_GAP = 8;
    static final int SIDEBAR_MINIMUM = 104;
    static final int SIDEBAR_MAXIMUM = 154;

    private ProfileViewerLayout() {
    }

    public static Layout calculate(int screenWidth, int screenHeight) {
        int safeWidth = Math.max(1, screenWidth);
        int safeHeight = Math.max(1, screenHeight);
        int horizontalMargin = Math.min(OUTER_MARGIN, Math.max(0, (safeWidth - 1) / 2));
        int verticalMargin = Math.min(OUTER_MARGIN, Math.max(0, (safeHeight - 1) / 2));
        int windowWidth = Math.max(1, Math.min(900, safeWidth - horizontalMargin * 2));
        int windowHeight = Math.max(1, Math.min(520, safeHeight - verticalMargin * 2));
        int windowX = (safeWidth - windowWidth) / 2;
        int windowY = (safeHeight - windowHeight) / 2;

        int headerHeight = Math.min(HEADER_HEIGHT, windowHeight);
        int identityHeight = Math.min(IDENTITY_HEIGHT, Math.max(0, windowHeight - headerHeight));
        int bodyY = windowY + headerHeight + identityHeight;
        int bodyHeight = Math.max(0, windowY + windowHeight - bodyY);
        int requestedSidebar = windowWidth < 430 ? Math.max(48, windowWidth / 3) : windowWidth / 5;
        int minimumContent = Math.min(90, Math.max(1, windowWidth / 2));
        int maximumSidebar = Math.max(0,
                Math.min(SIDEBAR_MAXIMUM, windowWidth - INNER_GAP - minimumContent));
        int minimumSidebar = Math.min(maximumSidebar, Math.min(SIDEBAR_MINIMUM, windowWidth / 4));
        int sidebarWidth = Math.clamp(requestedSidebar, minimumSidebar, maximumSidebar);
        int bodyRight = windowX + windowWidth;
        int contentX = Math.min(bodyRight, windowX + sidebarWidth + INNER_GAP);
        int contentWidth = Math.max(0, bodyRight - contentX);

        return new Layout(windowX, windowY, windowWidth, windowHeight,
                headerHeight, identityHeight,
                windowX, bodyY, sidebarWidth, bodyHeight,
                contentX, bodyY, contentWidth, bodyHeight);
    }

    public record Layout(int windowX, int windowY, int windowWidth, int windowHeight,
                         int headerHeight, int identityHeight,
                         int sidebarX, int sidebarY, int sidebarWidth, int sidebarHeight,
                         int contentX, int contentY, int contentWidth, int contentHeight) {
        public int windowRight() {
            return windowX + windowWidth;
        }

        public int windowBottom() {
            return windowY + windowHeight;
        }

        public int identityY() {
            return windowY + headerHeight;
        }

        public int identityRight() {
            return windowRight();
        }

        public boolean hasBody() {
            return sidebarWidth > 0 && contentWidth > 0
                    && sidebarHeight > 0 && contentHeight > 0;
        }
    }
}
