package cloudy.autume.addition.profile.ui;

import cloudy.autume.addition.QCloudyAdditionClient;
import cloudy.autume.addition.compat.MinecraftClientCompat;
import cloudy.autume.addition.config.AcaUiTheme;
import cloudy.autume.addition.config.ConfigManager;
import cloudy.autume.addition.config.VerticalScrollbar;
import cloudy.autume.addition.profile.ProfileDescriptor;
import cloudy.autume.addition.profile.ProfileException;
import cloudy.autume.addition.profile.ProfileLoadResult;
import cloudy.autume.addition.profile.ProfileRequest;
import cloudy.autume.addition.profile.ProfileSection;
import cloudy.autume.addition.profile.ProfileSectionId;
import cloudy.autume.addition.profile.ProfileService;
import cloudy.autume.addition.profile.ProfileSnapshot;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletionException;

/** QCA-styled, read-only browser for one player's SkyBlock profiles. */
public final class ProfileViewerScreen extends Screen {
    private static final int CATEGORY_HEIGHT = 23;
    private static final int CONTENT_GAP = 5;
    private static final int MAX_RENDER_LINES = 2_048;
    private static final int MAX_JSON_DEPTH = 5;
    private static final List<Category> CATEGORIES = List.of(
            new Category(ProfileSectionId.OVERVIEW, "Overview", "概览"),
            new Category(ProfileSectionId.GEAR, "Gear", "装备"),
            new Category(ProfileSectionId.ACCESSORIES, "Accessories", "饰品"),
            new Category(ProfileSectionId.PETS, "Pets", "宠物"),
            new Category(ProfileSectionId.INVENTORY, "Inventory", "背包"),
            new Category(ProfileSectionId.SKILLS, "Skills", "技能"),
            new Category(ProfileSectionId.SLAYER, "Slayer", "Slayer"),
            new Category(ProfileSectionId.MINIONS, "Minions", "Minion"),
            new Category(ProfileSectionId.BESTIARY, "Bestiary", "生物图鉴"),
            new Category(ProfileSectionId.COLLECTIONS, "Collections", "收藏"),
            new Category(ProfileSectionId.MINING, "Mining", "挖矿"),
            new Category(ProfileSectionId.CRIMSON_ISLE, "Crimson Isle", "绯红岛"),
            new Category(ProfileSectionId.RIFT, "The Rift", "裂隙"),
            new Category(ProfileSectionId.MISC, "Miscellaneous / Farming", "其他 / 农业"),
            new Category(ProfileSectionId.MUSEUM, "Museum", "博物馆"),
            new Category(ProfileSectionId.GARDEN, "Garden", "花园"),
            new Category(ProfileSectionId.MARKET, "Market", "市场")
    );

    private final @Nullable Screen parent;
    private final String initialTarget;
    private final ProfileService service;
    private final VerticalScrollbar sidebarScrollbar = new VerticalScrollbar();
    private final VerticalScrollbar contentScrollbar = new VerticalScrollbar();
    private final List<CategoryHit> categoryHits = new ArrayList<>();

    private ProfileViewerLayout.Layout layout = ProfileViewerLayout.calculate(1, 1);
    private @Nullable ProfileRequest activeRequest;
    private @Nullable ProfileSnapshot snapshot;
    private @Nullable ProfileException error;
    private @Nullable ProfileException sectionError;
    private @Nullable String pendingProfileId;
    private @Nullable ProfileSectionId loadingSection;
    private ProfileSectionId selectedSection = ProfileSectionId.OVERVIEW;
    private boolean started;
    private boolean closed;
    private boolean loading;
    private boolean partial;
    private boolean stale;
    private boolean fromSessionCache;
    private int sidebarScroll;
    private int sidebarMaximumScroll;
    private int contentScroll;
    private int contentMaximumScroll;
    private int previousProfileX;
    private int nextProfileX;
    private int profileButtonY;
    private int profileButtonSize;
    private int retryX;
    private int retryY;
    private int retryWidth;
    private long contentRevision;
    private long cachedContentRevision = -1;
    private int cachedContentWidth = -1;
    private boolean cachedContentChinese;
    private @Nullable ContentModel cachedContent;

    public ProfileViewerScreen(@Nullable Screen parent, String initialTarget, ProfileService service) {
        super(Component.literal("QCA Profile Viewer"));
        this.parent = parent;
        this.initialTarget = initialTarget == null ? "" : initialTarget.trim();
        this.service = service;
    }

    @Override
    protected void init() {
        if (!started) {
            started = true;
            request(null);
        }
    }

    private void request(@Nullable String profileId) {
        loading = true;
        error = null;
        sectionError = null;
        loadingSection = null;
        pendingProfileId = profileId;
        contentScroll = 0;
        contentScrollbar.cancelDrag();
        invalidateContent();
        String target = snapshot == null || snapshot.identity().uuid().isBlank()
                ? initialTarget : snapshot.identity().uuid();
        ProfileRequest request;
        try {
            request = service.load(target, profileId);
        } catch (RuntimeException exception) {
            acceptFailure(exception);
            return;
        }
        activeRequest = request;
        long generation = request.generation();
        request.future().whenComplete((result, failure) -> Minecraft.getInstance().execute(() -> {
            if (closed || !service.isCurrent(generation)) return;
            if (failure != null) acceptFailure(failure);
            else acceptResult(result);
        }));
    }

    private void acceptResult(ProfileLoadResult result) {
        if (result == null || result.snapshot() == null) {
            acceptFailure(new IllegalStateException("Profile service returned no snapshot"));
            return;
        }
        snapshot = result.snapshot();
        pendingProfileId = snapshot.selectedProfileId();
        loading = false;
        error = null;
        partial = snapshot.partial() || "PARTIAL".equals(result.status().name());
        stale = snapshot.stale() || "STALE".equals(result.status().name());
        fromSessionCache = result.fromSessionCache();
        ensureVisibleSelection();
        invalidateContent();
        requestSection(selectedSection);
    }

    private void requestSection(ProfileSectionId sectionId) {
        if (loading || loadingSection != null || snapshot == null || !lazySection(sectionId)) return;
        ProfileSection section = snapshot.section(sectionId).orElse(null);
        if (section == null || !"NOT_LOADED".equals(section.status().name())) return;
        loadingSection = sectionId;
        sectionError = null;
        contentScroll = 0;
        contentScrollbar.cancelDrag();
        invalidateContent();
        ProfileRequest request;
        try {
            request = service.loadSection(snapshot, sectionId);
        } catch (RuntimeException exception) {
            acceptSectionFailure(exception, sectionId);
            return;
        }
        activeRequest = request;
        long generation = request.generation();
        request.future().whenComplete((result, failure) -> Minecraft.getInstance().execute(() -> {
            if (closed || !service.isCurrent(generation)) return;
            if (failure != null) acceptSectionFailure(failure, sectionId);
            else {
                loadingSection = null;
                sectionError = null;
                acceptResult(result);
            }
        }));
    }

    private void acceptSectionFailure(Throwable throwable, ProfileSectionId sectionId) {
        Throwable cause = unwrap(throwable);
        sectionError = cause instanceof ProfileException profileException
                ? profileException
                : new ProfileException(ProfileException.Code.SERVICE_UNAVAILABLE,
                "This profile section is temporarily unavailable.");
        loadingSection = null;
        invalidateContent();
        if (!(cause instanceof ProfileException)) {
            QCloudyAdditionClient.LOGGER.warn("Profile section {} lookup failed", sectionId, cause);
        }
    }

    private static boolean lazySection(ProfileSectionId sectionId) {
        return sectionId == ProfileSectionId.MUSEUM || sectionId == ProfileSectionId.GARDEN;
    }

    private void acceptFailure(Throwable throwable) {
        Throwable cause = unwrap(throwable);
        error = cause instanceof ProfileException profileException
                ? profileException
                : new ProfileException(ProfileException.Code.SERVICE_UNAVAILABLE,
                "Profile service is temporarily unavailable.");
        loading = false;
        invalidateContent();
        if (!(cause instanceof ProfileException)) {
            QCloudyAdditionClient.LOGGER.warn("Profile lookup failed", cause);
        }
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) current = current.getCause();
        return current;
    }

    private void ensureVisibleSelection() {
        if (snapshot == null) return;
        if (snapshot.section(selectedSection).isPresent()) return;
        for (Category category : CATEGORIES) {
            if (snapshot.section(category.section()).isPresent()) {
                selectedSection = category.section();
                return;
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        layout = ProfileViewerLayout.calculate(width, height);
        graphics.fill(0, 0, width, height, AcaUiTheme.SCRIM);
        graphics.fill(layout.windowX() + 4, layout.windowY() + 5,
                layout.windowRight() + 5, layout.windowBottom() + 6, 0x66000000);
        AcaUiTheme.surface(graphics, layout.windowX(), layout.windowY(),
                layout.windowWidth(), layout.windowHeight(), AcaUiTheme.WINDOW);
        drawHeader(graphics, mouseX, mouseY);
        drawIdentity(graphics, mouseX, mouseY);
        if (layout.hasBody()) {
            drawSidebar(graphics, mouseX, mouseY);
            drawContent(graphics, mouseX, mouseY);
        }
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private void drawHeader(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int bottom = layout.windowY() + layout.headerHeight();
        graphics.fill(layout.windowX() + 1, layout.windowY() + 1,
                layout.windowRight() - 1, bottom, AcaUiTheme.HEADER);
        int titleY = layout.windowY() + Math.max(2, (layout.headerHeight() - font.lineHeight) / 2);
        graphics.text(font, Component.literal("QCA ").withStyle(ChatFormatting.AQUA)
                        .append(Component.literal(text("Profile Viewer", "玩家档案")).withStyle(ChatFormatting.BOLD)),
                layout.windowX() + 10, titleY, AcaUiTheme.TEXT, false);
        int closeSize = Math.min(16, Math.max(1, layout.headerHeight() - 12));
        int closeX = layout.windowRight() - closeSize - 8;
        int closeY = layout.windowY() + Math.max(1, (layout.headerHeight() - closeSize) / 2);
        boolean hovered = AcaUiTheme.contains(mouseX, mouseY, closeX, closeY, closeSize, closeSize);
        graphics.fill(closeX, closeY, closeX + closeSize, closeY + closeSize,
                hovered ? AcaUiTheme.DANGER : AcaUiTheme.CONTROL);
        graphics.outline(closeX, closeY, closeSize, closeSize, AcaUiTheme.BORDER);
        graphics.centeredText(font, "×", closeX + closeSize / 2, closeY + 3, AcaUiTheme.TEXT);
    }

    private void drawIdentity(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int y = layout.identityY();
        int bottom = y + layout.identityHeight();
        graphics.fill(layout.windowX() + 1, y, layout.windowRight() - 1, bottom, AcaUiTheme.CARD);
        String name = snapshot == null ? initialTarget : snapshot.identity().name();
        if (name == null || name.isBlank()) name = text("Unknown player", "未知玩家");
        drawFitted(graphics, Component.literal(name).withStyle(ChatFormatting.BOLD),
                layout.windowX() + 10, y + 7, Math.max(1, layout.windowWidth() / 3), AcaUiTheme.TEXT);
        String detail = snapshot == null ? text("Preparing lookup", "正在准备查询")
                : compactUuid(snapshot.identity().uuid());
        drawFitted(graphics, Component.literal(detail), layout.windowX() + 10, y + 23,
                Math.max(1, layout.windowWidth() / 3), AcaUiTheme.TEXT_DIM);

        drawStatusBadges(graphics, y);
        drawProfileSelector(graphics, mouseX, mouseY, y);
    }

    private void drawStatusBadges(GuiGraphicsExtractor graphics, int identityY) {
        int x = layout.windowX() + Math.max(130, layout.windowWidth() / 3);
        int y = identityY + 8;
        if (loading || loadingSection != null) {
            x = drawBadge(graphics, text("Loading", "加载中"), x, y, AcaUiTheme.ACCENT) + 4;
        }
        if (stale) x = drawBadge(graphics, text("Stale", "旧缓存"), x, y, 0xFFE3A72F) + 4;
        if (partial) x = drawBadge(graphics, text("Partial", "部分数据"), x, y, 0xFFE3A72F) + 4;
        if (fromSessionCache) drawBadge(graphics, text("Cached", "会话缓存"), x, y, AcaUiTheme.SUCCESS);
    }

    private int drawBadge(GuiGraphicsExtractor graphics, String label, int x, int y, int color) {
        int badgeWidth = font.width(label) + 10;
        if (x + badgeWidth >= layout.windowRight() - 8) return x;
        graphics.fill(x, y, x + badgeWidth, y + 14, AcaUiTheme.CONTROL);
        graphics.outline(x, y, badgeWidth, 14, color);
        graphics.text(font, label, x + 5, y + 3, color, false);
        return x + badgeWidth;
    }

    private void drawProfileSelector(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int identityY) {
        List<ProfileDescriptor> profiles = snapshot == null ? List.of() : snapshot.profiles();
        profileButtonSize = 18;
        int selectorWidth = Math.min(230, Math.max(90, layout.windowWidth() / 3));
        int right = layout.windowRight() - 10;
        previousProfileX = right - selectorWidth;
        nextProfileX = right - profileButtonSize;
        profileButtonY = identityY + Math.max(4, (layout.identityHeight() - profileButtonSize) / 2);
        int valueX = previousProfileX + profileButtonSize + 3;
        int valueWidth = Math.max(1, nextProfileX - valueX - 3);
        boolean enabled = profiles.size() > 1 && !loading;
        drawSmallButton(graphics, "‹", previousProfileX, profileButtonY, profileButtonSize,
                enabled && AcaUiTheme.contains(mouseX, mouseY, previousProfileX, profileButtonY,
                        profileButtonSize, profileButtonSize), enabled);
        drawSmallButton(graphics, "›", nextProfileX, profileButtonY, profileButtonSize,
                enabled && AcaUiTheme.contains(mouseX, mouseY, nextProfileX, profileButtonY,
                        profileButtonSize, profileButtonSize), enabled);
        graphics.fill(valueX, profileButtonY, valueX + valueWidth, profileButtonY + profileButtonSize,
                AcaUiTheme.CONTROL);
        graphics.outline(valueX, profileButtonY, valueWidth, profileButtonSize, AcaUiTheme.BORDER);
        String label = selectedProfileLabel();
        drawCenteredFitted(graphics, label, valueX, profileButtonY, valueWidth, profileButtonSize,
                profiles.isEmpty() ? AcaUiTheme.TEXT_DIM : AcaUiTheme.TEXT);
    }

    private void drawSmallButton(GuiGraphicsExtractor graphics, String label, int x, int y, int size,
                                 boolean hovered, boolean enabled) {
        graphics.fill(x, y, x + size, y + size,
                hovered ? AcaUiTheme.CARD_HOVER : AcaUiTheme.CONTROL);
        graphics.outline(x, y, size, size, enabled ? AcaUiTheme.BORDER : AcaUiTheme.BORDER_SOFT);
        graphics.centeredText(font, label, x + size / 2, y + 4,
                enabled ? AcaUiTheme.TEXT : AcaUiTheme.TEXT_DIM);
    }

    private String selectedProfileLabel() {
        if (snapshot == null || snapshot.profiles().isEmpty()) return text("No profile", "无档案");
        String selectedId = snapshot.selectedProfileId();
        ProfileDescriptor profile = snapshot.profiles().stream()
                .filter(value -> value.profileId().equals(selectedId)).findFirst()
                .orElse(snapshot.profiles().getFirst());
        String mode = profile.gameMode() == null || profile.gameMode().isBlank()
                ? "" : " · " + profile.gameMode();
        return profile.cuteName() + mode;
    }

    private void drawSidebar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.fill(layout.sidebarX() + 1, layout.sidebarY(),
                layout.sidebarX() + layout.sidebarWidth(), layout.sidebarY() + layout.sidebarHeight(),
                AcaUiTheme.SIDEBAR);
        categoryHits.clear();
        int inset = 7;
        int viewportX = layout.sidebarX() + inset;
        int viewportY = layout.sidebarY() + inset;
        int viewportWidth = Math.max(1, layout.sidebarWidth() - inset * 2 - VerticalScrollbar.WIDTH - 2);
        int viewportHeight = Math.max(0, layout.sidebarHeight() - inset * 2);
        sidebarMaximumScroll = Math.max(0, CATEGORIES.size() * CATEGORY_HEIGHT - viewportHeight);
        sidebarScroll = Math.clamp(sidebarScroll, 0, sidebarMaximumScroll);
        int y = viewportY - sidebarScroll;
        graphics.enableScissor(viewportX, viewportY,
                viewportX + viewportWidth, viewportY + viewportHeight);
        for (Category category : CATEGORIES) {
            boolean selected = category.section() == selectedSection;
            boolean hovered = AcaUiTheme.contains(mouseX, mouseY, viewportX, y,
                    viewportWidth, CATEGORY_HEIGHT - 2);
            graphics.fill(viewportX, y, viewportX + viewportWidth, y + CATEGORY_HEIGHT - 2,
                    selected ? 0xFF303A3F : hovered ? AcaUiTheme.CARD_HOVER : AcaUiTheme.SIDEBAR);
            if (selected) graphics.fill(viewportX, y, viewportX + 3, y + CATEGORY_HEIGHT - 2,
                    AcaUiTheme.ACCENT);
            drawFitted(graphics, Component.literal(category.label(chinese())), viewportX + 8, y + 6,
                    Math.max(1, viewportWidth - 12), selected ? AcaUiTheme.TEXT : AcaUiTheme.TEXT_MUTED);
            if (y + CATEGORY_HEIGHT > viewportY && y < viewportY + viewportHeight) {
                categoryHits.add(new CategoryHit(category, viewportX, y,
                        viewportWidth, CATEGORY_HEIGHT - 2));
            }
            y += CATEGORY_HEIGHT;
        }
        graphics.disableScissor();
        sidebarScrollbar.update(layout.sidebarX() + layout.sidebarWidth() - VerticalScrollbar.WIDTH - 2,
                viewportY, viewportHeight, sidebarMaximumScroll, sidebarScroll);
        sidebarScrollbar.draw(graphics, mouseX, mouseY, AcaUiTheme.ACCENT);
    }

    private void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.fill(layout.contentX(), layout.contentY(), layout.contentX() + layout.contentWidth(),
                layout.contentY() + layout.contentHeight(), AcaUiTheme.CONTENT);
        int inset = 8;
        int viewportX = layout.contentX() + inset;
        int viewportY = layout.contentY() + inset;
        int viewportWidth = Math.max(1, layout.contentWidth() - inset * 2 - VerticalScrollbar.WIDTH - 2);
        int viewportHeight = Math.max(0, layout.contentHeight() - inset * 2);
        retryX = -1;
        retryY = -1;
        retryWidth = 0;

        ContentModel content = contentModel(viewportWidth);
        contentMaximumScroll = Math.max(0, content.totalHeight() - viewportHeight);
        contentScroll = Math.clamp(contentScroll, 0, contentMaximumScroll);
        graphics.enableScissor(viewportX, viewportY,
                viewportX + viewportWidth, viewportY + viewportHeight);
        int viewportBottom = viewportY + viewportHeight;
        for (LaidOutLine line : content.lines()) {
            int y = viewportY - contentScroll + line.offsetY();
            if (y + line.height() <= viewportY || y >= viewportBottom) continue;
            drawRenderLine(graphics, line, viewportX, y, viewportWidth, mouseX, mouseY);
        }
        graphics.disableScissor();
        contentScrollbar.update(layout.contentX() + layout.contentWidth() - VerticalScrollbar.WIDTH - 2,
                viewportY, viewportHeight, contentMaximumScroll, contentScroll);
        contentScrollbar.draw(graphics, mouseX, mouseY, AcaUiTheme.ACCENT);
    }

    private List<RenderLine> contentLines() {
        List<RenderLine> result = new ArrayList<>();
        if (error != null && snapshot == null) {
            result.add(new RenderLine(LineKind.TITLE, text("Profile lookup failed", "档案查询失败"), 0));
            result.add(new RenderLine(LineKind.WARNING, errorText(error), 0));
            Duration retryAfter = error.retryAfter();
            if (retryAfter != null && !retryAfter.isZero() && !retryAfter.isNegative()) {
                result.add(new RenderLine(LineKind.TEXT,
                        text("Try again in ", "请稍后重试：") + retryAfter.toSeconds() + "s", 0));
            }
            result.add(new RenderLine(LineKind.RETRY, text("Retry", "重试"), 0));
            return result;
        }
        if (snapshot == null) {
            result.add(new RenderLine(LineKind.TITLE, text("Loading profile", "正在加载档案"), 0));
            result.add(new RenderLine(LineKind.TEXT,
                    text("Contacting the QCA profile service…", "正在连接 QCA 档案服务……"), 0));
            return result;
        }
        if (error != null) {
            result.add(new RenderLine(LineKind.WARNING,
                    text("Refresh failed; showing the previous snapshot. ", "刷新失败，正在显示之前的快照。 ")
                            + errorText(error), 0));
            result.add(new RenderLine(LineKind.RETRY, text("Retry", "重试"), 0));
        } else if (loading) {
            result.add(new RenderLine(LineKind.NOTICE,
                    text("Loading the selected profile…", "正在加载所选档案……"), 0));
        }
        if (stale) {
            result.add(new RenderLine(LineKind.WARNING,
                    text("This is an older cached snapshot and may be out of date.",
                            "这是较旧的缓存快照，内容可能已经过时。"), 0));
        }
        if (partial) {
            result.add(new RenderLine(LineKind.WARNING,
                    text("Some profile sections are unavailable or private.", "部分档案分类不可用或已设为私密。"), 0));
        }

        Category category = category(selectedSection);
        result.add(new RenderLine(LineKind.TITLE, category.label(chinese()), 0));
        if (loadingSection == selectedSection) {
            result.add(new RenderLine(LineKind.NOTICE,
                    text("Loading this profile section…", "正在加载这个档案分类……"), 0));
            return result;
        }
        if (sectionError != null && lazySection(selectedSection)) {
            result.add(new RenderLine(LineKind.WARNING, errorText(sectionError), 0));
            result.add(new RenderLine(LineKind.RETRY, text("Retry", "重试"), 0));
            return result;
        }
        ProfileSection section = snapshot.section(selectedSection).orElse(null);
        if (section == null) {
            result.add(new RenderLine(LineKind.TEXT,
                    text("This section was not returned by the service.", "服务没有返回这个分类。"), 0));
            return result;
        }
        String status = section.status().name();
        if (!"AVAILABLE".equals(status) && !"STALE".equals(status)) {
            result.add(new RenderLine("PRIVATE".equals(status) ? LineKind.NOTICE : LineKind.WARNING,
                    sectionMessage(section, status), 0));
            return result;
        }
        if ("STALE".equals(status)) {
            result.add(new RenderLine(LineKind.WARNING,
                    section.message() == null || section.message().isBlank()
                            ? text("This section is cached.", "此分类来自缓存。")
                            : section.message(), 0));
        }
        JsonObject payload = section.payload();
        if (payload == null || payload.isEmpty()) {
            result.add(new RenderLine(LineKind.TEXT, text("No data to display.", "没有可显示的数据。"), 0));
        } else {
            flattenObject(result, payload, 0);
        }
        if (result.size() >= MAX_RENDER_LINES) {
            result.add(new RenderLine(LineKind.WARNING,
                    text("Additional rows were omitted to keep the screen responsive.",
                            "为保持界面流畅，其余内容已省略。"), 0));
        }
        return result;
    }

    private String sectionMessage(ProfileSection section, String status) {
        if (marketPricesNotLoaded(section.id(), status)) {
            return text("Market prices were not loaded for this profile request. No value estimate is being shown.",
                    "本次档案请求未加载市场价格，因此不会显示任何估值。");
        }
        if (section.message() != null && !section.message().isBlank()) return section.message();
        return switch (status) {
            case "PRIVATE" -> text("This section is private.", "此分类已设为私密。");
            case "NOT_FOUND" -> text("No data was found for this section.", "没有找到此分类的数据。");
            case "NOT_LOADED" -> text("This section has not been loaded.", "此分类尚未加载。");
            default -> text("This section is currently unavailable.", "此分类暂时不可用。");
        };
    }

    static boolean marketPricesNotLoaded(ProfileSectionId sectionId, String status) {
        return sectionId == ProfileSectionId.MARKET && "NOT_LOADED".equals(status);
    }

    private void flattenObject(List<RenderLine> lines, JsonObject object, int depth) {
        if (depth > MAX_JSON_DEPTH || lines.size() >= MAX_RENDER_LINES) return;
        for (var entry : object.entrySet()) {
            if (lines.size() >= MAX_RENDER_LINES) return;
            flatten(lines, entry.getKey(), readableKey(entry.getKey()), entry.getValue(), depth);
        }
    }

    private void flatten(List<RenderLine> lines, String rawKey, String label,
                         JsonElement element, int depth) {
        if (lines.size() >= MAX_RENDER_LINES) return;
        if (element == null || element.isJsonNull()) {
            lines.add(new RenderLine(LineKind.PAIR, label + ": —", depth));
            return;
        }
        if (element.isJsonPrimitive()) {
            String value = primitive(element);
            if (shouldRedactOpaqueItemData(rawKey, value)) {
                value = text("Item data is decoded by the service or unavailable.",
                        "物品数据应由服务端解码；当前内容不可用。");
            }
            lines.add(new RenderLine(LineKind.PAIR, label + ": " + value, depth));
            return;
        }
        if (depth >= MAX_JSON_DEPTH) {
            lines.add(new RenderLine(LineKind.PAIR, label + ": …", depth));
            return;
        }
        if (element.isJsonObject()) {
            lines.add(new RenderLine(LineKind.SUBTITLE, label, depth));
            flattenObject(lines, element.getAsJsonObject(), depth + 1);
            return;
        }
        JsonArray array = element.getAsJsonArray();
        if (array.isEmpty()) {
            lines.add(new RenderLine(LineKind.PAIR, label + ": —", depth));
            return;
        }
        boolean allPrimitive = true;
        for (JsonElement value : array) allPrimitive &= value == null || value.isJsonNull() || value.isJsonPrimitive();
        if (allPrimitive) {
            StringBuilder joined = new StringBuilder();
            int count = Math.min(array.size(), 20);
            for (int index = 0; index < count; index++) {
                if (index > 0) joined.append(", ");
                joined.append(primitive(array.get(index)));
            }
            if (array.size() > count) joined.append(" … (+").append(array.size() - count).append(')');
            String value = joined.toString();
            if (shouldRedactOpaqueItemData(rawKey, value)) {
                value = text("Item data is decoded by the service or unavailable.",
                        "物品数据应由服务端解码；当前内容不可用。");
            }
            lines.add(new RenderLine(LineKind.PAIR, label + ": " + value, depth));
            return;
        }
        lines.add(new RenderLine(LineKind.SUBTITLE, label + " (" + array.size() + ")", depth));
        int count = Math.min(array.size(), 256);
        for (int index = 0; index < count && lines.size() < MAX_RENDER_LINES; index++) {
            flatten(lines, "", "#" + (index + 1), array.get(index), depth + 1);
        }
    }

    static boolean shouldRedactOpaqueItemData(String rawKey, String value) {
        if (rawKey == null || value == null || value.length() < 96) return false;
        String normalized = rawKey.trim()
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .toLowerCase(Locale.ROOT).replace('-', '_');
        return "data".equals(normalized) || "item_bytes".equals(normalized);
    }

    private ContentModel contentModel(int width) {
        boolean useChinese = chinese();
        if (cachedContent != null && cachedContentRevision == contentRevision
                && cachedContentWidth == width && cachedContentChinese == useChinese) {
            return cachedContent;
        }
        List<LaidOutLine> laidOut = new ArrayList<>();
        int offsetY = 0;
        for (RenderLine line : contentLines()) {
            int indent = Math.min(36, line.depth() * 8);
            int lineWidth = Math.max(1, width - indent);
            List<FormattedCharSequence> wrapped;
            int height;
            if (line.kind() == LineKind.RETRY) {
                wrapped = List.of();
                height = 22;
            } else {
                wrapped = List.copyOf(font.split(Component.literal(line.text()),
                        Math.max(1, lineWidth - 14)));
                height = Math.max(20, wrapped.size() * (font.lineHeight + 2) + 8);
            }
            laidOut.add(new LaidOutLine(line, offsetY, height, wrapped));
            offsetY += height + CONTENT_GAP;
        }
        int totalHeight = laidOut.isEmpty() ? 0 : offsetY - CONTENT_GAP;
        cachedContent = new ContentModel(List.copyOf(laidOut), totalHeight);
        cachedContentRevision = contentRevision;
        cachedContentWidth = width;
        cachedContentChinese = useChinese;
        return cachedContent;
    }

    private void invalidateContent() {
        contentRevision++;
        cachedContent = null;
    }

    private void drawRenderLine(GuiGraphicsExtractor graphics, LaidOutLine laidOut, int x, int y, int width,
                                int mouseX, int mouseY) {
        RenderLine line = laidOut.line();
        int height = laidOut.height();
        int indent = Math.min(36, line.depth() * 8);
        int lineX = x + indent;
        int lineWidth = Math.max(1, width - indent);
        if (line.kind() == LineKind.RETRY) {
            retryX = lineX;
            retryY = y;
            retryWidth = Math.min(100, lineWidth);
            AcaUiTheme.button(graphics, font, line.text(), retryX, retryY, retryWidth, height,
                    AcaUiTheme.contains(mouseX, mouseY, retryX, retryY, retryWidth, height), false);
            return;
        }
        int background = switch (line.kind()) {
            case TITLE -> 0xFF20292D;
            case SUBTITLE -> AcaUiTheme.CARD;
            case WARNING -> 0xFF332D21;
            case NOTICE -> 0xFF203038;
            default -> AcaUiTheme.CARD;
        };
        int border = switch (line.kind()) {
            case TITLE, NOTICE -> AcaUiTheme.ACCENT_DARK;
            case WARNING -> 0xFFE3A72F;
            default -> AcaUiTheme.BORDER_SOFT;
        };
        int color = switch (line.kind()) {
            case TITLE -> AcaUiTheme.TEXT;
            case SUBTITLE -> AcaUiTheme.TEXT;
            case WARNING -> 0xFFF3D17A;
            default -> AcaUiTheme.TEXT_MUTED;
        };
        graphics.fill(lineX, y, lineX + lineWidth, y + height, background);
        graphics.outline(lineX, y, lineWidth, height, border);
        if (line.kind() == LineKind.TITLE || line.kind() == LineKind.SUBTITLE) {
            graphics.fill(lineX, y, lineX + 3, y + height,
                    line.kind() == LineKind.TITLE ? AcaUiTheme.ACCENT : AcaUiTheme.ACCENT_DARK);
        }
        int textY = y + 5;
        for (FormattedCharSequence value : laidOut.wrapped()) {
            graphics.text(font, value, lineX + 8, textY, color, false);
            textY += font.lineHeight + 2;
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        VerticalScrollbar.Interaction contentClick = contentScrollbar.mouseClicked(
                click.button(), click.x(), click.y(), contentScroll);
        if (contentClick.consumed()) {
            contentScroll = contentClick.scroll();
            return true;
        }
        VerticalScrollbar.Interaction sidebarClick = sidebarScrollbar.mouseClicked(
                click.button(), click.x(), click.y(), sidebarScroll);
        if (sidebarClick.consumed()) {
            sidebarScroll = sidebarClick.scroll();
            return true;
        }
        if (super.mouseClicked(click, doubled)) return true;
        if (click.button() != 0) return false;
        int closeSize = Math.min(16, Math.max(1, layout.headerHeight() - 12));
        int closeX = layout.windowRight() - closeSize - 8;
        int closeY = layout.windowY() + Math.max(1, (layout.headerHeight() - closeSize) / 2);
        if (AcaUiTheme.contains(click.x(), click.y(), closeX, closeY, closeSize, closeSize)) {
            onClose();
            return true;
        }
        if (!loading && snapshot != null && snapshot.profiles().size() > 1) {
            if (AcaUiTheme.contains(click.x(), click.y(), previousProfileX, profileButtonY,
                    profileButtonSize, profileButtonSize)) {
                cycleProfile(-1);
                return true;
            }
            if (AcaUiTheme.contains(click.x(), click.y(), nextProfileX, profileButtonY,
                    profileButtonSize, profileButtonSize)) {
                cycleProfile(1);
                return true;
            }
        }
        for (CategoryHit hit : categoryHits) {
            if (!hit.contains(click.x(), click.y())) continue;
            selectedSection = hit.category().section();
            contentScroll = 0;
            contentScrollbar.cancelDrag();
            sectionError = null;
            invalidateContent();
            requestSection(selectedSection);
            return true;
        }
        if (retryX >= 0 && (error != null || sectionError != null)
                && AcaUiTheme.contains(click.x(), click.y(), retryX, retryY, retryWidth, 22)) {
            if (sectionError != null && lazySection(selectedSection)) requestSection(selectedSection);
            else request(pendingProfileId);
            return true;
        }
        return false;
    }

    private void cycleProfile(int direction) {
        if (snapshot == null || snapshot.profiles().size() < 2) return;
        List<ProfileDescriptor> profiles = snapshot.profiles();
        int current = 0;
        for (int index = 0; index < profiles.size(); index++) {
            if (profiles.get(index).profileId().equals(snapshot.selectedProfileId())) {
                current = index;
                break;
            }
        }
        int next = Math.floorMod(current + direction, profiles.size());
        request(profiles.get(next).profileId());
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
        VerticalScrollbar.Interaction contentDrag = contentScrollbar.mouseDragged(
                click.button(), click.y(), contentScroll);
        if (contentDrag.consumed()) {
            contentScroll = contentDrag.scroll();
            return true;
        }
        VerticalScrollbar.Interaction sidebarDrag = sidebarScrollbar.mouseDragged(
                click.button(), click.y(), sidebarScroll);
        if (sidebarDrag.consumed()) {
            sidebarScroll = sidebarDrag.scroll();
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        VerticalScrollbar.Interaction contentRelease = contentScrollbar.mouseReleased(
                click.button(), click.y(), contentScroll);
        if (contentRelease.consumed()) {
            contentScroll = contentRelease.scroll();
            return true;
        }
        VerticalScrollbar.Interaction sidebarRelease = sidebarScrollbar.mouseReleased(
                click.button(), click.y(), sidebarScroll);
        if (sidebarRelease.consumed()) {
            sidebarScroll = sidebarRelease.scroll();
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (contentScrollbar.dragging()) return true;
        if (sidebarScrollbar.dragging()) return true;
        if (AcaUiTheme.contains(mouseX, mouseY, layout.sidebarX(), layout.sidebarY(),
                layout.sidebarWidth(), layout.sidebarHeight())) {
            VerticalScrollbar.Interaction interaction = sidebarScrollbar.mouseScrolled(
                    vertical, CATEGORY_HEIGHT, sidebarScroll);
            if (interaction.consumed()) {
                sidebarScroll = interaction.scroll();
                return true;
            }
        }
        if (AcaUiTheme.contains(mouseX, mouseY, layout.contentX(), layout.contentY(),
                layout.contentWidth(), layout.contentHeight())) {
            VerticalScrollbar.Interaction interaction = contentScrollbar.mouseScrolled(
                    vertical, 28, contentScroll);
            if (interaction.consumed()) {
                contentScroll = interaction.scroll();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public void onClose() {
        closed = true;
        if (activeRequest != null) activeRequest.cancel();
        service.cancelCurrent();
        MinecraftClientCompat.setScreen(minecraft, parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private String errorText(ProfileException exception) {
        String code = exception.code().name();
        return switch (code) {
            case "INVALID_TARGET" -> text("Enter a valid Minecraft name or UUID.", "请输入有效的 Minecraft 名称或 UUID。");
            case "PLAYER_NOT_FOUND" -> text("That Minecraft player could not be found.", "没有找到该 Minecraft 玩家。");
            case "PROFILE_NOT_FOUND" -> text("That SkyBlock profile could not be found.", "没有找到该 SkyBlock 档案。");
            case "NO_SKYBLOCK_PROFILES" -> text("This player has no visible SkyBlock profiles.",
                    "该玩家没有可见的 SkyBlock 档案。");
            case "RATE_LIMITED" -> text("The profile service is busy. Please wait before retrying.",
                    "档案服务请求过多，请稍后再试。");
            case "UNAUTHORIZED" -> text("The profile service is not authorised to fetch this data.",
                    "档案服务目前无权获取这些数据。");
            case "UNSUPPORTED_SCHEMA" -> text("This QCA build cannot read the profile response format.",
                    "当前 QCA 版本无法读取档案响应格式。");
            case "INVALID_RESPONSE", "RESPONSE_TOO_LARGE" -> text("The profile service returned invalid data.",
                    "档案服务返回了无效数据。");
            case "UPSTREAM_UNAVAILABLE" -> text("Hypixel's profile service is temporarily unavailable.",
                    "Hypixel 档案服务暂时不可用。");
            case "SERVICE_UNAVAILABLE" -> text("The QCA profile service is temporarily unavailable.",
                    "QCA 档案服务暂时不可用。");
            case "CANCELLED" -> text("The profile lookup was cancelled.", "档案查询已取消。");
            default -> text("The profile service is temporarily unavailable.", "档案服务暂时不可用。");
        };
    }

    private static String primitive(JsonElement value) {
        if (value == null || value.isJsonNull()) return "—";
        String text = value.getAsString();
        if (text == null || text.isBlank()) return "—";
        return text.length() <= 512 ? text : text.substring(0, 509) + "…";
    }

    private static String readableKey(String value) {
        if (value == null || value.isBlank()) return "Data";
        String spaced = value.replace('_', ' ').replace('-', ' ')
                .replaceAll("([a-z0-9])([A-Z])", "$1 $2").trim();
        if (spaced.isEmpty()) return "Data";
        return spaced.substring(0, 1).toUpperCase(Locale.ROOT) + spaced.substring(1);
    }

    private static String compactUuid(String uuid) {
        if (uuid == null || uuid.isBlank()) return "—";
        return uuid.length() <= 20 ? uuid : uuid.substring(0, 8) + "…" + uuid.substring(uuid.length() - 8);
    }

    private void drawFitted(GuiGraphicsExtractor graphics, Component value, int x, int y,
                            int availableWidth, int color) {
        String text = value.getString();
        if (font.width(text) <= availableWidth) {
            graphics.text(font, value, x, y, color, false);
            return;
        }
        graphics.text(font, font.plainSubstrByWidth(text, Math.max(1, availableWidth - font.width("…"))) + "…",
                x, y, color, false);
    }

    private void drawCenteredFitted(GuiGraphicsExtractor graphics, String value, int x, int y,
                                    int availableWidth, int height, int color) {
        String fitted = value;
        if (font.width(fitted) > availableWidth - 8) {
            fitted = font.plainSubstrByWidth(fitted, Math.max(1, availableWidth - 8 - font.width("…"))) + "…";
        }
        graphics.text(font, fitted, x + Math.max(3, (availableWidth - font.width(fitted)) / 2),
                y + Math.max(2, (height - font.lineHeight) / 2), color, false);
    }

    private static Category category(ProfileSectionId section) {
        return CATEGORIES.stream().filter(value -> value.section() == section).findFirst()
                .orElse(CATEGORIES.getFirst());
    }

    private boolean chinese() {
        return "zh_cn".equals(ConfigManager.get().language);
    }

    private String text(String english, String chinese) {
        return chinese() ? chinese : english;
    }

    private record Category(ProfileSectionId section, String english, String chinese) {
        String label(boolean useChinese) {
            return useChinese ? chinese : english;
        }
    }

    private record CategoryHit(Category category, int x, int y, int width, int height) {
        boolean contains(double mouseX, double mouseY) {
            return AcaUiTheme.contains(mouseX, mouseY, x, y, width, height);
        }
    }

    private enum LineKind {
        TITLE, SUBTITLE, PAIR, TEXT, NOTICE, WARNING, RETRY
    }

    private record RenderLine(LineKind kind, String text, int depth) {
    }

    private record LaidOutLine(RenderLine line, int offsetY, int height,
                               List<FormattedCharSequence> wrapped) {
    }

    private record ContentModel(List<LaidOutLine> lines, int totalHeight) {
    }
}
