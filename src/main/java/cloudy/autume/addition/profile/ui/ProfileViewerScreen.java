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
import cloudy.autume.addition.profile.market.MarketTooltipPrice;
import cloudy.autume.addition.profile.market.MarketTooltipPriceBatch;
import cloudy.autume.addition.profile.market.MarketTooltipPriceService;
import cloudy.autume.addition.profile.market.MarketTooltipQuery;
import cloudy.autume.addition.profile.market.PriceTooltipFormatter;
import cloudy.autume.addition.profile.market.PriceTooltipLayout;
import cloudy.autume.addition.profile.market.PriceTooltipRow;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Read-only, semantic SkyBlock profile browser. Transport JSON is mapped into
 * cards, progress bars and item grids before anything is rendered.
 */
public final class ProfileViewerScreen extends Screen {
    private static final int CATEGORY_HEIGHT = 25;
    private static final int BLOCK_GAP = 7;
    private static final int ITEM_CELL = 22;
    private static final int LABEL_ORANGE = 0xFFFFAA00;
    private static final int PRICE_CYAN = 0xFF00AAAA;
    private static final int UNIT_GRAY = 0xFFAAAAAA;
    private final @Nullable Screen parent;
    private final String initialTarget;
    private final ProfileService service;
    private final MarketTooltipPriceService priceService;
    private final VerticalScrollbar sidebarScrollbar = new VerticalScrollbar();
    private final VerticalScrollbar contentScrollbar = new VerticalScrollbar();
    private final List<CategoryHit> categoryHits = new ArrayList<>();
    private final List<ItemHit> itemHits = new ArrayList<>();

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
    private int retryX = -1;
    private int retryY = -1;
    private int retryWidth;
    private int contentClipTop;
    private int contentClipBottom;

    private long contentRevision;
    private long cachedViewRevision = -1;
    private @Nullable ProfileSectionId cachedViewSection;
    private boolean cachedViewChinese;
    private ProfilePresentationMapper.@Nullable SectionView cachedView;

    private @Nullable ProfileItemHover hoveredItem;
    private @Nullable MarketTooltipQuery hoveredQuery;
    private @Nullable MarketTooltipPrice hoveredPrice;
    private @Nullable CompletableFuture<MarketTooltipPriceBatch> activePriceRequest;

    public ProfileViewerScreen(@Nullable Screen parent,
                               String initialTarget,
                               ProfileService service,
                               MarketTooltipPriceService priceService) {
        super(Component.literal("QCA Profile Viewer"));
        this.parent = parent;
        this.initialTarget = initialTarget == null ? "" : initialTarget.trim();
        this.service = service;
        this.priceService = priceService;
    }

    @Override
    protected void init() {
        if (!started) {
            started = true;
            request(null);
        }
    }

    private void request(@Nullable String profileId) {
        cancelPriceRequest();
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
        while ((current instanceof CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) current = current.getCause();
        return current;
    }

    private void ensureVisibleSelection() {
        if (snapshot == null || snapshot.section(selectedSection).isPresent()) return;
        for (Category category : categories()) {
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
        } else {
            updateHoveredItem(null);
        }
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        drawHoveredTooltip(graphics, mouseX, mouseY);
    }

    private void drawHeader(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int bottom = layout.windowY() + layout.headerHeight();
        graphics.fill(layout.windowX() + 1, layout.windowY() + 1,
                layout.windowRight() - 1, bottom, AcaUiTheme.HEADER);
        int titleY = layout.windowY() + Math.max(2, (layout.headerHeight() - font.lineHeight) / 2);
        graphics.text(font, Component.literal("QCA ").withStyle(ChatFormatting.AQUA)
                        .append(Component.literal(text("Player Profile", "玩家档案")).withStyle(ChatFormatting.BOLD)),
                layout.windowX() + 10, titleY, AcaUiTheme.TEXT, false);
        int closeSize = closeSize();
        int closeX = closeX(closeSize);
        int closeY = closeY(closeSize);
        boolean hovered = AcaUiTheme.contains(mouseX, mouseY, closeX, closeY, closeSize, closeSize);
        graphics.fill(closeX, closeY, closeX + closeSize, closeY + closeSize,
                hovered ? AcaUiTheme.DANGER : AcaUiTheme.CONTROL);
        graphics.outline(closeX, closeY, closeSize, closeSize, AcaUiTheme.BORDER);
        graphics.centeredText(font, "×", closeX + closeSize / 2, closeY + 3, AcaUiTheme.TEXT);
    }

    private void drawIdentity(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int y = layout.identityY();
        graphics.fill(layout.windowX() + 1, y, layout.windowRight() - 1,
                y + layout.identityHeight(), AcaUiTheme.CARD);
        graphics.item(new ItemStack(Items.PLAYER_HEAD), layout.windowX() + 10, y + 8);
        String name = snapshot == null ? initialTarget : snapshot.identity().name();
        if (name == null || name.isBlank()) name = text("Unknown player", "未知玩家");
        String detail = text("Preparing profile", "正在准备档案");
        if (snapshot != null) {
            ProfilePresentationMapper.HeaderView header =
                    ProfilePresentationMapper.header(snapshot, chinese());
            name = (header.rank().isBlank() ? "" : header.rank() + " ") + header.name();
            detail = header.profileName();
            if (!header.mode().isBlank() && !"—".equals(header.mode())) detail += " · " + header.mode();
            if (header.memberCount() > 1) {
                detail += " · " + header.memberCount() + " " + text("members", "名成员");
            }
        }
        int nameX = layout.windowX() + 32;
        drawFitted(graphics, name, nameX, y + 7,
                Math.max(1, layout.windowWidth() / 3), AcaUiTheme.TEXT);
        drawFitted(graphics, detail, nameX, y + 23,
                Math.max(1, layout.windowWidth() / 3), AcaUiTheme.TEXT_MUTED);
        drawStatusBadges(graphics, y);
        drawProfileSelector(graphics, mouseX, mouseY, y);
    }

    private void drawStatusBadges(GuiGraphicsExtractor graphics, int identityY) {
        int x = layout.windowX() + Math.max(190, layout.windowWidth() / 3);
        int y = identityY + 8;
        if (loading || loadingSection != null) {
            x = drawBadge(graphics, text("Loading", "加载中"), x, y, AcaUiTheme.ACCENT) + 4;
        }
        if (stale) x = drawBadge(graphics, text("Stale", "旧缓存"), x, y, LABEL_ORANGE) + 4;
        if (partial) x = drawBadge(graphics, text("Partial", "部分数据"), x, y, LABEL_ORANGE) + 4;
        if (fromSessionCache) drawBadge(graphics, text("Cached", "会话缓存"), x, y, AcaUiTheme.SUCCESS);
    }

    private int drawBadge(GuiGraphicsExtractor graphics, String label, int x, int y, int color) {
        int badgeWidth = font.width(label) + 10;
        if (x + badgeWidth >= layout.windowRight() - Math.max(110, layout.windowWidth() / 3)) return x;
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
        drawCenteredFitted(graphics, selectedProfileLabel(), valueX, profileButtonY,
                valueWidth, profileButtonSize,
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
        ProfileDescriptor profile = snapshot.profiles().stream()
                .filter(value -> value.profileId().equals(snapshot.selectedProfileId()))
                .findFirst().orElse(snapshot.profiles().getFirst());
        return profile.cuteName();
    }

    private void drawSidebar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.fill(layout.sidebarX() + 1, layout.sidebarY(),
                layout.sidebarX() + layout.sidebarWidth(), layout.sidebarY() + layout.sidebarHeight(),
                AcaUiTheme.SIDEBAR);
        categoryHits.clear();
        int inset = 6;
        int viewportX = layout.sidebarX() + inset;
        int viewportY = layout.sidebarY() + inset;
        int viewportWidth = Math.max(1,
                layout.sidebarWidth() - inset * 2 - VerticalScrollbar.WIDTH - 2);
        int viewportHeight = Math.max(0, layout.sidebarHeight() - inset * 2);
        sidebarMaximumScroll = Math.max(0, categories().size() * CATEGORY_HEIGHT - viewportHeight);
        sidebarScroll = Math.clamp(sidebarScroll, 0, sidebarMaximumScroll);
        int y = viewportY - sidebarScroll;
        graphics.enableScissor(viewportX, viewportY,
                viewportX + viewportWidth, viewportY + viewportHeight);
        for (Category category : categories()) {
            boolean selected = category.section() == selectedSection;
            boolean hovered = AcaUiTheme.contains(mouseX, mouseY, viewportX, y,
                    viewportWidth, CATEGORY_HEIGHT - 2);
            graphics.fill(viewportX, y, viewportX + viewportWidth, y + CATEGORY_HEIGHT - 2,
                    selected ? 0xFF303A3F : hovered ? AcaUiTheme.CARD_HOVER : AcaUiTheme.SIDEBAR);
            if (selected) graphics.fill(viewportX, y, viewportX + 3,
                    y + CATEGORY_HEIGHT - 2, AcaUiTheme.ACCENT);
            graphics.item(category.icon().copy(), viewportX + 6, y + 3);
            drawFitted(graphics, category.label(chinese()), viewportX + 28, y + 7,
                    Math.max(1, viewportWidth - 32),
                    selected ? AcaUiTheme.TEXT : AcaUiTheme.TEXT_MUTED);
            if (y + CATEGORY_HEIGHT > viewportY && y < viewportY + viewportHeight) {
                categoryHits.add(new CategoryHit(category, viewportX, y,
                        viewportWidth, CATEGORY_HEIGHT - 2));
            }
            y += CATEGORY_HEIGHT;
        }
        graphics.disableScissor();
        sidebarScrollbar.update(layout.sidebarX() + layout.sidebarWidth()
                        - VerticalScrollbar.WIDTH - 2,
                viewportY, viewportHeight, sidebarMaximumScroll, sidebarScroll);
        sidebarScrollbar.draw(graphics, mouseX, mouseY, AcaUiTheme.ACCENT);
    }

    private void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.fill(layout.contentX(), layout.contentY(), layout.contentX() + layout.contentWidth(),
                layout.contentY() + layout.contentHeight(), AcaUiTheme.CONTENT);
        int inset = 8;
        int viewportX = layout.contentX() + inset;
        int viewportY = layout.contentY() + inset;
        int viewportWidth = Math.max(1,
                layout.contentWidth() - inset * 2 - VerticalScrollbar.WIDTH - 2);
        int viewportHeight = Math.max(0, layout.contentHeight() - inset * 2);
        contentClipTop = viewportY;
        contentClipBottom = viewportY + viewportHeight;
        retryX = -1;
        retryY = -1;
        retryWidth = 0;
        itemHits.clear();

        int measuredHeight = measureContent(viewportWidth);
        contentMaximumScroll = Math.max(0, measuredHeight - viewportHeight);
        contentScroll = Math.clamp(contentScroll, 0, contentMaximumScroll);
        graphics.enableScissor(viewportX, viewportY,
                viewportX + viewportWidth, viewportY + viewportHeight);
        renderContent(graphics, viewportX, viewportY - contentScroll, viewportWidth, mouseX, mouseY);
        graphics.disableScissor();
        contentScrollbar.update(layout.contentX() + layout.contentWidth()
                        - VerticalScrollbar.WIDTH - 2,
                viewportY, viewportHeight, contentMaximumScroll, contentScroll);
        contentScrollbar.draw(graphics, mouseX, mouseY, AcaUiTheme.ACCENT);

        ProfileItemHover frameHover = null;
        for (ItemHit hit : itemHits) {
            if (hit.contains(mouseX, mouseY)) {
                frameHover = hit.hover();
                break;
            }
        }
        updateHoveredItem(frameHover);
    }

    private int measureContent(int width) {
        int height = 0;
        for (Banner banner : banners()) height += noticeHeight(banner.message(), width) + BLOCK_GAP;
        if (snapshot == null) {
            height += 31 + BLOCK_GAP;
            String message = error == null
                    ? text("Contacting the QCA profile service…", "正在连接 QCA 档案服务……")
                    : errorText(error);
            height += noticeHeight(message, width) + BLOCK_GAP;
        } else {
            ProfilePresentationMapper.SectionView view = currentView();
            height += 31 + BLOCK_GAP;
            for (ProfilePresentationMapper.Block block : view.blocks()) {
                height += blockHeight(block, width) + BLOCK_GAP;
            }
        }
        if (needsRetry()) height += 22 + BLOCK_GAP;
        return Math.max(0, height - BLOCK_GAP);
    }

    private void renderContent(GuiGraphicsExtractor graphics, int x, int y, int width,
                               int mouseX, int mouseY) {
        int cursor = y;
        for (Banner banner : banners()) {
            int height = noticeHeight(banner.message(), width);
            drawNotice(graphics, banner.message(), x, cursor, width, height, banner.color());
            cursor += height + BLOCK_GAP;
        }
        if (snapshot == null) {
            String title = error == null
                    ? text("Loading profile", "正在加载档案")
                    : text("Profile lookup failed", "档案查询失败");
            drawSectionHeader(graphics, title, new ItemStack(Items.PLAYER_HEAD), x, cursor, width);
            cursor += 31 + BLOCK_GAP;
            String message = error == null
                    ? text("Contacting the QCA profile service…", "正在连接 QCA 档案服务……")
                    : errorText(error);
            int height = noticeHeight(message, width);
            drawNotice(graphics, message, x, cursor, width, height,
                    error == null ? AcaUiTheme.ACCENT_DARK : LABEL_ORANGE);
            cursor += height + BLOCK_GAP;
        } else {
            ProfilePresentationMapper.SectionView view = currentView();
            drawSectionHeader(graphics, view.title(), category(selectedSection).icon(),
                    x, cursor, width);
            cursor += 31 + BLOCK_GAP;
            for (ProfilePresentationMapper.Block block : view.blocks()) {
                int height = blockHeight(block, width);
                drawBlock(graphics, block, x, cursor, width, height, mouseX, mouseY);
                cursor += height + BLOCK_GAP;
            }
        }
        if (needsRetry()) {
            retryX = x;
            retryY = cursor;
            retryWidth = Math.min(110, width);
            AcaUiTheme.button(graphics, font, text("Retry", "重试"), retryX, retryY,
                    retryWidth, 22,
                    AcaUiTheme.contains(mouseX, mouseY, retryX, retryY, retryWidth, 22), false);
        }
    }

    private List<Banner> banners() {
        List<Banner> result = new ArrayList<>();
        if (snapshot != null && error != null) {
            result.add(new Banner(text("Refresh failed; the previous snapshot remains visible. ",
                    "刷新失败，之前的快照仍然可见。 ") + errorText(error), LABEL_ORANGE));
        } else if (snapshot != null && loading) {
            result.add(new Banner(text("Loading the selected profile…", "正在加载所选档案……"),
                    AcaUiTheme.ACCENT_DARK));
        }
        if (stale) result.add(new Banner(text(
                "This is an older cached snapshot and may be out of date.",
                "这是较旧的缓存快照，内容可能已经过时。"), LABEL_ORANGE));
        if (partial) result.add(new Banner(text(
                "Some profile data is private, unavailable, or safely omitted.",
                "部分档案数据为私密、不可用或已安全省略。"), LABEL_ORANGE));
        return result;
    }

    private ProfilePresentationMapper.SectionView currentView() {
        boolean useChinese = chinese();
        if (cachedView != null && cachedViewRevision == contentRevision
                && cachedViewSection == selectedSection && cachedViewChinese == useChinese) {
            return cachedView;
        }
        Category selected = category(selectedSection);
        if (loadingSection == selectedSection) {
            cachedView = new ProfilePresentationMapper.SectionView(
                    selected.label(useChinese), "", List.of(new ProfilePresentationMapper.NoticeBlock(
                    text("Loading", "加载中"),
                    text("Loading this profile section…", "正在加载这个档案分类……"))));
        } else if (sectionError != null && lazySection(selectedSection)) {
            cachedView = new ProfilePresentationMapper.SectionView(
                    selected.label(useChinese), "", List.of(new ProfilePresentationMapper.NoticeBlock(
                    text("Unavailable", "暂时不可用"), errorText(sectionError))));
        } else {
            ProfileSection section = snapshot == null ? null : snapshot.section(selectedSection).orElse(null);
            if (section == null) {
                cachedView = new ProfilePresentationMapper.SectionView(
                        selected.label(useChinese), "", List.of(new ProfilePresentationMapper.EmptyBlock(
                        text("This section was not returned by the service.", "服务没有返回这个分类。"))));
            } else if (!section.available()) {
                cachedView = new ProfilePresentationMapper.SectionView(
                        selected.label(useChinese), "", List.of(new ProfilePresentationMapper.EmptyBlock(
                        sectionMessage(section, section.status().name()))));
            } else {
                cachedView = ProfilePresentationMapper.section(snapshot, selectedSection, useChinese);
            }
        }
        cachedViewRevision = contentRevision;
        cachedViewSection = selectedSection;
        cachedViewChinese = useChinese;
        return cachedView;
    }

    private int blockHeight(ProfilePresentationMapper.Block block, int width) {
        if (block instanceof ProfilePresentationMapper.StatGridBlock value) {
            int columns = gridColumns(width, 150, 3);
            int rows = divideCeil(value.cards().size(), columns);
            return 12 + titled(value.title()) + rows * 41 + Math.max(0, rows - 1) * 5 + 8;
        }
        if (block instanceof ProfilePresentationMapper.ProgressBlock value) {
            int columns = gridColumns(width, 260, 2);
            int rows = divideCeil(value.entries().size(), columns);
            return 12 + titled(value.title()) + rows * 34 + Math.max(0, rows - 1) * 5 + 8;
        }
        if (block instanceof ProfilePresentationMapper.ItemGridBlock value) {
            int columns = itemColumns(value, width);
            int rows = divideCeil(Math.max(1, value.minimumSlots()), columns);
            return 12 + titled(value.title()) + rows * ITEM_CELL + 8;
        }
        if (block instanceof ProfilePresentationMapper.PetGridBlock value) {
            int columns = gridColumns(width, 230, 2);
            int rows = divideCeil(value.pets().size(), columns);
            return 12 + titled(value.title()) + rows * 46 + Math.max(0, rows - 1) * 5 + 8;
        }
        if (block instanceof ProfilePresentationMapper.ListBlock value) {
            return 12 + titled(value.title()) + Math.max(1, value.rows().size()) * 15 + 8;
        }
        if (block instanceof ProfilePresentationMapper.NoticeBlock value) {
            return noticeBlockHeight(value.title(), value.message(), width);
        }
        if (block instanceof ProfilePresentationMapper.EmptyBlock value) {
            return noticeHeight(value.message(), width);
        }
        return 30;
    }

    private void drawBlock(GuiGraphicsExtractor graphics, ProfilePresentationMapper.Block block,
                           int x, int y, int width, int height, int mouseX, int mouseY) {
        if (block instanceof ProfilePresentationMapper.StatGridBlock value) {
            drawStatGrid(graphics, value, x, y, width, height);
        } else if (block instanceof ProfilePresentationMapper.ProgressBlock value) {
            drawProgressGrid(graphics, value, x, y, width, height);
        } else if (block instanceof ProfilePresentationMapper.ItemGridBlock value) {
            drawItemGrid(graphics, value, x, y, width, height, mouseX, mouseY);
        } else if (block instanceof ProfilePresentationMapper.PetGridBlock value) {
            drawPetGrid(graphics, value, x, y, width, height);
        } else if (block instanceof ProfilePresentationMapper.ListBlock value) {
            drawListBlock(graphics, value, x, y, width, height);
        } else if (block instanceof ProfilePresentationMapper.NoticeBlock value) {
            drawNoticeBlock(graphics, value, x, y, width, height);
        } else if (block instanceof ProfilePresentationMapper.EmptyBlock value) {
            drawNotice(graphics, value.message(), x, y, width, height, AcaUiTheme.BORDER);
        }
    }

    private void drawStatGrid(GuiGraphicsExtractor graphics,
                              ProfilePresentationMapper.StatGridBlock block,
                              int x, int y, int width, int height) {
        panel(graphics, x, y, width, height);
        int top = y + 7;
        if (!block.title().isBlank()) {
            drawFitted(graphics, block.title(), x + 8, top, width - 16, AcaUiTheme.TEXT);
            top += 19;
        }
        int columns = gridColumns(width, 150, 3);
        int gap = 5;
        int cardWidth = Math.max(1, (width - 16 - gap * (columns - 1)) / columns);
        for (int index = 0; index < block.cards().size(); index++) {
            ProfilePresentationMapper.StatCard card = block.cards().get(index);
            int column = index % columns;
            int row = index / columns;
            int cardX = x + 8 + column * (cardWidth + gap);
            int cardY = top + row * 46;
            graphics.fill(cardX, cardY, cardX + cardWidth, cardY + 40, AcaUiTheme.CONTROL);
            graphics.outline(cardX, cardY, cardWidth, 40, toneColor(card.tone()));
            drawFitted(graphics, card.label(), cardX + 6, cardY + 6,
                    cardWidth - 12, AcaUiTheme.TEXT_MUTED);
            drawFitted(graphics, Component.literal(card.value()).withStyle(ChatFormatting.BOLD),
                    cardX + 6, cardY + 21, cardWidth - 12, toneColor(card.tone()));
        }
    }

    private void drawProgressGrid(GuiGraphicsExtractor graphics,
                                  ProfilePresentationMapper.ProgressBlock block,
                                  int x, int y, int width, int height) {
        panel(graphics, x, y, width, height);
        int top = y + 7;
        if (!block.title().isBlank()) {
            drawFitted(graphics, block.title(), x + 8, top, width - 16, AcaUiTheme.TEXT);
            top += 19;
        }
        int columns = gridColumns(width, 260, 2);
        int gap = 5;
        int entryWidth = Math.max(1, (width - 16 - gap * (columns - 1)) / columns);
        for (int index = 0; index < block.entries().size(); index++) {
            ProfilePresentationMapper.ProgressEntry entry = block.entries().get(index);
            int column = index % columns;
            int row = index / columns;
            int entryX = x + 8 + column * (entryWidth + gap);
            int entryY = top + row * 39;
            drawFitted(graphics, entry.label(), entryX, entryY,
                    Math.max(1, entryWidth - 64), AcaUiTheme.TEXT);
            int levelWidth = font.width(entry.level());
            graphics.text(font, entry.level(), entryX + Math.max(0, entryWidth - levelWidth),
                    entryY, AcaUiTheme.TEXT_MUTED, false);
            drawFitted(graphics, entry.detail(), entryX, entryY + 12,
                    entryWidth, AcaUiTheme.TEXT_DIM);
            int barY = entryY + 25;
            graphics.fill(entryX, barY, entryX + entryWidth, barY + 6, AcaUiTheme.CONTROL);
            int filled = (int) Math.round(entryWidth * entry.progress());
            if (filled > 0) graphics.fill(entryX, barY, entryX + filled, barY + 6, AcaUiTheme.ACCENT);
            graphics.outline(entryX, barY, entryWidth, 6, AcaUiTheme.BORDER_SOFT);
        }
    }

    private void drawItemGrid(GuiGraphicsExtractor graphics,
                              ProfilePresentationMapper.ItemGridBlock block,
                              int x, int y, int width, int height, int mouseX, int mouseY) {
        panel(graphics, x, y, width, height);
        int top = y + 7;
        if (!block.title().isBlank()) {
            drawFitted(graphics, block.title(), x + 8, top, width - 16, AcaUiTheme.TEXT);
            top += 19;
        }
        int columns = itemColumns(block, width);
        Map<Integer, ProfilePresentationMapper.ItemView> bySlot = new HashMap<>();
        for (ProfilePresentationMapper.ItemView item : block.items()) {
            if (item.slot() >= 0 && item.slot() < block.minimumSlots()) bySlot.put(item.slot(), item);
        }
        int slots = Math.max(1, block.minimumSlots());
        for (int slot = 0; slot < slots; slot++) {
            int column = slot % columns;
            int row = slot / columns;
            int cellX = x + 8 + column * ITEM_CELL;
            int cellY = top + row * ITEM_CELL;
            ProfilePresentationMapper.ItemView item = bySlot.get(slot);
            boolean hovered = item != null && AcaUiTheme.contains(mouseX, mouseY,
                    cellX, cellY, ITEM_CELL - 2, ITEM_CELL - 2);
            graphics.fill(cellX, cellY, cellX + ITEM_CELL - 2, cellY + ITEM_CELL - 2,
                    hovered ? AcaUiTheme.CARD_HOVER : AcaUiTheme.CONTROL);
            graphics.outline(cellX, cellY, ITEM_CELL - 2, ITEM_CELL - 2,
                    item == null ? AcaUiTheme.BORDER_SOFT : rarityColor(item.rarity()));
            if (item == null) continue;
            graphics.item(ProfileItemVisuals.icon(item), cellX + 2, cellY + 2);
            if (item.count() > 1) {
                String count = Integer.toString(item.count());
                graphics.text(font, count, cellX + ITEM_CELL - 3 - font.width(count),
                        cellY + ITEM_CELL - 11, AcaUiTheme.TEXT, true);
            }
            if (cellY + ITEM_CELL > contentClipTop && cellY < contentClipBottom) {
                itemHits.add(new ItemHit(new ProfileItemHover(item.itemId(), item.variantKey(),
                        item.count(), item.displayName(), item.rarity()),
                        cellX, cellY, ITEM_CELL - 2, ITEM_CELL - 2));
            }
        }
    }

    private void drawPetGrid(GuiGraphicsExtractor graphics,
                             ProfilePresentationMapper.PetGridBlock block,
                             int x, int y, int width, int height) {
        panel(graphics, x, y, width, height);
        int top = y + 7;
        if (!block.title().isBlank()) {
            drawFitted(graphics, block.title(), x + 8, top, width - 16, AcaUiTheme.TEXT);
            top += 19;
        }
        int columns = gridColumns(width, 230, 2);
        int gap = 5;
        int cardWidth = Math.max(1, (width - 16 - gap * (columns - 1)) / columns);
        for (int index = 0; index < block.pets().size(); index++) {
            ProfilePresentationMapper.PetView pet = block.pets().get(index);
            int column = index % columns;
            int row = index / columns;
            int cardX = x + 8 + column * (cardWidth + gap);
            int cardY = top + row * 51;
            graphics.fill(cardX, cardY, cardX + cardWidth, cardY + 45,
                    pet.active() ? 0xFF20352F : AcaUiTheme.CONTROL);
            graphics.outline(cardX, cardY, cardWidth, 45,
                    pet.active() ? AcaUiTheme.SUCCESS : rarityColor(pet.rarity()));
            graphics.item(new ItemStack(Items.PLAYER_HEAD), cardX + 6, cardY + 7);
            drawFitted(graphics, pet.name(), cardX + 28, cardY + 7,
                    cardWidth - 35, rarityColor(pet.rarity()));
            String detail = pet.rarity();
            if (!pet.heldItem().isBlank() && !"—".equals(pet.heldItem())) detail += " · " + pet.heldItem();
            drawFitted(graphics, detail, cardX + 28, cardY + 21,
                    cardWidth - 35, AcaUiTheme.TEXT_MUTED);
            drawFitted(graphics, ProfilePresentationMapper.formatNumber(pet.experience()) + " XP",
                    cardX + 28, cardY + 33, cardWidth - 35, AcaUiTheme.TEXT_DIM);
        }
    }

    private void drawListBlock(GuiGraphicsExtractor graphics,
                               ProfilePresentationMapper.ListBlock block,
                               int x, int y, int width, int height) {
        panel(graphics, x, y, width, height);
        int top = y + 7;
        if (!block.title().isBlank()) {
            drawFitted(graphics, block.title(), x + 8, top, width - 16, AcaUiTheme.TEXT);
            top += 19;
        }
        for (String row : block.rows()) {
            drawFitted(graphics, row, x + 10, top, width - 20, AcaUiTheme.TEXT_MUTED);
            top += 15;
        }
    }

    private void drawNoticeBlock(GuiGraphicsExtractor graphics,
                                 ProfilePresentationMapper.NoticeBlock block,
                                 int x, int y, int width, int height) {
        panel(graphics, x, y, width, height);
        int top = y + 7;
        if (!block.title().isBlank()) {
            drawFitted(graphics, block.title(), x + 8, top, width - 16, LABEL_ORANGE);
            top += 17;
        }
        for (FormattedCharSequence line : font.split(Component.literal(block.message()),
                Math.max(1, width - 16))) {
            graphics.text(font, line, x + 8, top, AcaUiTheme.TEXT_MUTED, false);
            top += font.lineHeight + 2;
        }
    }

    private void drawSectionHeader(GuiGraphicsExtractor graphics, String title, ItemStack icon,
                                   int x, int y, int width) {
        graphics.fill(x, y, x + width, y + 31, 0xFF20292D);
        graphics.outline(x, y, width, 31, AcaUiTheme.ACCENT_DARK);
        graphics.fill(x, y, x + 3, y + 31, AcaUiTheme.ACCENT);
        graphics.item(icon.copy(), x + 9, y + 7);
        drawFitted(graphics, Component.literal(title).withStyle(ChatFormatting.BOLD),
                x + 32, y + 11, width - 40, AcaUiTheme.TEXT);
    }

    private void drawNotice(GuiGraphicsExtractor graphics, String message,
                            int x, int y, int width, int height, int border) {
        graphics.fill(x, y, x + width, y + height, 0xFF292820);
        graphics.outline(x, y, width, height, border);
        int textY = y + 7;
        for (FormattedCharSequence line : font.split(Component.literal(message),
                Math.max(1, width - 16))) {
            graphics.text(font, line, x + 8, textY,
                    border == AcaUiTheme.ACCENT_DARK ? AcaUiTheme.TEXT_MUTED : 0xFFF3D17A, false);
            textY += font.lineHeight + 2;
        }
    }

    private void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, AcaUiTheme.CARD);
        graphics.outline(x, y, width, height, AcaUiTheme.BORDER_SOFT);
    }

    private int noticeHeight(String message, int width) {
        int lines = Math.max(1, font.split(Component.literal(message), Math.max(1, width - 16)).size());
        return Math.max(29, 14 + lines * (font.lineHeight + 2));
    }

    private int noticeBlockHeight(String title, String message, int width) {
        int lines = Math.max(1, font.split(Component.literal(message), Math.max(1, width - 16)).size());
        return 14 + (title.isBlank() ? 0 : 17) + lines * (font.lineHeight + 2);
    }

    private static int titled(String title) {
        return title == null || title.isBlank() ? 0 : 19;
    }

    private static int divideCeil(int value, int divisor) {
        return Math.max(1, (Math.max(0, value) + divisor - 1) / divisor);
    }

    private static int gridColumns(int width, int preferredWidth, int maximum) {
        return Math.clamp(Math.max(1, (width - 16) / Math.max(1, preferredWidth)), 1, maximum);
    }

    private static int itemColumns(ProfilePresentationMapper.ItemGridBlock block, int width) {
        int available = Math.max(1, (width - 16) / ITEM_CELL);
        return Math.max(1, Math.min(block.columns(), available));
    }

    private static int toneColor(ProfilePresentationMapper.StatTone tone) {
        return switch (tone) {
            case ACCENT -> AcaUiTheme.ACCENT;
            case SUCCESS -> AcaUiTheme.SUCCESS;
            case WARNING -> LABEL_ORANGE;
            case NORMAL -> AcaUiTheme.TEXT;
        };
    }

    private static int rarityColor(String rarity) {
        if (rarity == null) return AcaUiTheme.BORDER;
        return switch (rarity.toUpperCase(Locale.ROOT)) {
            case "COMMON" -> 0xFFFFFFFF;
            case "UNCOMMON" -> 0xFF55FF55;
            case "RARE" -> 0xFF5555FF;
            case "EPIC" -> 0xFFAA00AA;
            case "LEGENDARY" -> 0xFFFFAA00;
            case "MYTHIC" -> 0xFFFF55FF;
            case "DIVINE" -> 0xFF55FFFF;
            case "SPECIAL", "VERY SPECIAL" -> 0xFFFF5555;
            default -> AcaUiTheme.BORDER;
        };
    }

    private void updateHoveredItem(@Nullable ProfileItemHover next) {
        if (next == null) {
            if (hoveredItem != null) cancelPriceRequest();
            hoveredItem = null;
            hoveredQuery = null;
            hoveredPrice = null;
            return;
        }
        MarketTooltipQuery nextQuery = null;
        if (!next.itemId().isBlank()) {
            try {
                nextQuery = new MarketTooltipQuery(next.itemId(),
                        next.variantKey().isBlank() ? null : next.variantKey(), next.count());
            } catch (IllegalArgumentException ignored) {
                // A visual item can still be displayed when its market identity is unusable.
            }
        }
        if (next.equals(hoveredItem) && Objects.equals(nextQuery, hoveredQuery)) return;
        cancelPriceRequest();
        hoveredItem = next;
        hoveredQuery = nextQuery;
        hoveredPrice = null;
        if (nextQuery == null) return;
        MarketTooltipQuery requested = nextQuery;
        CompletableFuture<MarketTooltipPriceBatch> future;
        try {
            future = priceService.load(List.of(requested));
        } catch (RuntimeException ignored) {
            return;
        }
        activePriceRequest = future;
        future.whenComplete((batch, failure) -> Minecraft.getInstance().execute(() -> {
            if (closed || !requested.equals(hoveredQuery) || future != activePriceRequest) return;
            activePriceRequest = null;
            if (failure == null && batch != null) hoveredPrice = batch.price(requested).orElse(null);
        }));
    }

    private void cancelPriceRequest() {
        CompletableFuture<MarketTooltipPriceBatch> future = activePriceRequest;
        activePriceRequest = null;
        if (future != null) future.cancel(false);
        hoveredPrice = null;
    }

    private void drawHoveredTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        ProfileItemHover hover = hoveredItem;
        if (hover == null) return;
        String displayName = hover.displayName().isBlank()
                ? ProfilePresentationMapper.humanize(hover.itemId()) : hover.displayName();
        String rarity = hover.rarity();
        PriceTooltipLayout prices = hoveredPrice == null
                ? new PriceTooltipLayout(List.of(), 0, 0, 0)
                : PriceTooltipFormatter.format(hoveredPrice, font::width);
        int textWidth = Math.max(font.width(displayName), rarity.isBlank() ? 0 : font.width(rarity));
        textWidth = Math.max(textWidth, prices.totalWidth());
        int boxWidth = Math.min(Math.max(120, textWidth + 12), Math.max(120, width - 8));
        int baseLines = rarity.isBlank() ? 1 : 2;
        int boxHeight = 10 + baseLines * (font.lineHeight + 2)
                + prices.rows().size() * (font.lineHeight + 3);
        int boxX = Math.min(mouseX + 13, Math.max(4, width - boxWidth - 4));
        int boxY = Math.max(4, Math.min(mouseY - 8, Math.max(4, height - boxHeight - 4)));
        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xF50A1727);
        graphics.outline(boxX, boxY, boxWidth, boxHeight,
                rarity.isBlank() ? AcaUiTheme.ACCENT_DARK : rarityColor(rarity));
        int lineY = boxY + 6;
        drawFitted(graphics, displayName, boxX + 6, lineY, boxWidth - 12,
                rarity.isBlank() ? AcaUiTheme.TEXT : rarityColor(rarity));
        lineY += font.lineHeight + 2;
        if (!rarity.isBlank()) {
            drawFitted(graphics, rarity, boxX + 6, lineY, boxWidth - 12, rarityColor(rarity));
            lineY += font.lineHeight + 2;
        }
        for (PriceTooltipRow row : prices.rows()) {
            graphics.text(font, row.label(), boxX + 6, lineY, LABEL_ORANGE, false);
            int valueX = boxX + 6 + prices.valueColumnX();
            String value = row.value();
            int suffix = value.lastIndexOf(" (");
            if (suffix > 0) {
                String total = value.substring(0, suffix);
                String each = value.substring(suffix);
                graphics.text(font, total, valueX, lineY, PRICE_CYAN, false);
                graphics.text(font, each, valueX + font.width(total), lineY, UNIT_GRAY, false);
            } else {
                graphics.text(font, value, valueX, lineY, PRICE_CYAN, false);
            }
            lineY += font.lineHeight + 3;
        }
    }

    private String sectionMessage(ProfileSection section, String status) {
        if (marketPricesNotLoaded(section.id(), status)) {
            return text("Market prices were not loaded for this profile request.",
                    "本次档案请求未加载市场价格。");
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

    /** Kept as a regression guard even though raw profile JSON is no longer rendered. */
    static boolean shouldRedactOpaqueItemData(String rawKey, String value) {
        if (rawKey == null || value == null || value.length() < 96) return false;
        String normalized = rawKey.trim()
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .toLowerCase(Locale.ROOT).replace('-', '_');
        return "data".equals(normalized) || "item_bytes".equals(normalized);
    }

    private boolean needsRetry() {
        return error != null || sectionError != null;
    }

    private void invalidateContent() {
        contentRevision++;
        cachedView = null;
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
        int closeSize = closeSize();
        if (AcaUiTheme.contains(click.x(), click.y(), closeX(closeSize), closeY(closeSize),
                closeSize, closeSize)) {
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
            cancelPriceRequest();
            hoveredItem = null;
            hoveredQuery = null;
            invalidateContent();
            requestSection(selectedSection);
            return true;
        }
        if (retryX >= 0 && needsRetry()
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
        request(profiles.get(Math.floorMod(current + direction, profiles.size())).profileId());
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
        if (contentScrollbar.dragging() || sidebarScrollbar.dragging()) return true;
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
                    vertical, 32, contentScroll);
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
        cancelPriceRequest();
        if (activeRequest != null) activeRequest.cancel();
        service.cancelCurrent();
        MinecraftClientCompat.setScreen(minecraft, parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private String errorText(ProfileException exception) {
        return switch (exception.code().name()) {
            case "INVALID_TARGET" -> text("Enter a valid Minecraft name or UUID.",
                    "请输入有效的 Minecraft 名称或 UUID。");
            case "PLAYER_NOT_FOUND" -> text("That Minecraft player could not be found.",
                    "没有找到该 Minecraft 玩家。");
            case "PROFILE_NOT_FOUND" -> text("That SkyBlock profile could not be found.",
                    "没有找到该 SkyBlock 档案。");
            case "NO_SKYBLOCK_PROFILES" -> text("This player has no visible SkyBlock profiles.",
                    "该玩家没有可见的 SkyBlock 档案。");
            case "RATE_LIMITED" -> text("The profile service is busy. Please wait before retrying.",
                    "档案服务请求过多，请稍后再试。");
            case "UNAUTHORIZED" -> text("The profile service is not authorised to fetch this data.",
                    "档案服务目前无权获取这些数据。");
            case "UNSUPPORTED_SCHEMA" -> text("This QCA build cannot read the profile response format.",
                    "当前 QCA 版本无法读取档案响应格式。");
            case "INVALID_RESPONSE", "RESPONSE_TOO_LARGE" -> text(
                    "The profile service returned invalid data.", "档案服务返回了无效数据。");
            case "UPSTREAM_UNAVAILABLE" -> text("Hypixel's profile service is temporarily unavailable.",
                    "Hypixel 档案服务暂时不可用。");
            case "SERVICE_UNAVAILABLE" -> text("The QCA profile service is temporarily unavailable.",
                    "QCA 档案服务暂时不可用。");
            case "CANCELLED" -> text("The profile lookup was cancelled.", "档案查询已取消。");
            default -> text("The profile service is temporarily unavailable.", "档案服务暂时不可用。");
        };
    }

    private int closeSize() {
        return Math.min(16, Math.max(1, layout.headerHeight() - 12));
    }

    private int closeX(int size) {
        return layout.windowRight() - size - 8;
    }

    private int closeY(int size) {
        return layout.windowY() + Math.max(1, (layout.headerHeight() - size) / 2);
    }

    private void drawFitted(GuiGraphicsExtractor graphics, String value, int x, int y,
                            int availableWidth, int color) {
        drawFitted(graphics, Component.literal(value), x, y, availableWidth, color);
    }

    private void drawFitted(GuiGraphicsExtractor graphics, Component value, int x, int y,
                            int availableWidth, int color) {
        String raw = value.getString();
        if (font.width(raw) <= availableWidth) {
            graphics.text(font, value, x, y, color, false);
            return;
        }
        String fitted = font.plainSubstrByWidth(raw,
                Math.max(1, availableWidth - font.width("…"))) + "…";
        graphics.text(font, fitted, x, y, color, false);
    }

    private void drawCenteredFitted(GuiGraphicsExtractor graphics, String value, int x, int y,
                                    int availableWidth, int height, int color) {
        String fitted = value;
        if (font.width(fitted) > availableWidth - 8) {
            fitted = font.plainSubstrByWidth(fitted,
                    Math.max(1, availableWidth - 8 - font.width("…"))) + "…";
        }
        graphics.text(font, fitted, x + Math.max(3, (availableWidth - font.width(fitted)) / 2),
                y + Math.max(2, (height - font.lineHeight) / 2), color, false);
    }

    private static Category category(ProfileSectionId section) {
        return categories().stream().filter(value -> value.section() == section).findFirst()
                .orElse(categories().getFirst());
    }

    /** Keeps Minecraft registry-backed ItemStack creation out of pure unit-test class init. */
    private static List<Category> categories() {
        return CategoryHolder.CATEGORIES;
    }

    private static final class CategoryHolder {
        private static final List<Category> CATEGORIES = List.of(
                new Category(ProfileSectionId.OVERVIEW, "Overview", "概览", new ItemStack(Items.PLAYER_HEAD)),
                new Category(ProfileSectionId.GEAR, "Gear", "装备", redLeatherChestplate()),
                new Category(ProfileSectionId.ACCESSORIES, "Accessories", "饰品", new ItemStack(Items.NETHER_STAR)),
                new Category(ProfileSectionId.PETS, "Pets", "宠物", new ItemStack(Items.BONE)),
                new Category(ProfileSectionId.INVENTORY, "Inventory", "背包", new ItemStack(Items.ENDER_CHEST)),
                new Category(ProfileSectionId.SKILLS, "Skills", "技能", new ItemStack(Items.EXPERIENCE_BOTTLE)),
                new Category(ProfileSectionId.SLAYER, "Slayer", "Slayer", new ItemStack(Items.IRON_SWORD)),
                new Category(ProfileSectionId.MINIONS, "Minions", "Minion", new ItemStack(Items.ARMOR_STAND)),
                new Category(ProfileSectionId.BESTIARY, "Bestiary", "生物图鉴", new ItemStack(Items.ZOMBIE_HEAD)),
                new Category(ProfileSectionId.COLLECTIONS, "Collections", "收藏", new ItemStack(Items.BOOK)),
                new Category(ProfileSectionId.MINING, "Mining", "挖矿", new ItemStack(Items.DIAMOND_PICKAXE)),
                new Category(ProfileSectionId.CRIMSON_ISLE, "Crimson Isle", "绯红岛", new ItemStack(Items.MAGMA_CREAM)),
                new Category(ProfileSectionId.RIFT, "The Rift", "裂隙", new ItemStack(Items.ENDER_PEARL)),
                new Category(ProfileSectionId.MISC, "Farming & Other", "农业与其他", new ItemStack(Items.WHEAT)),
                new Category(ProfileSectionId.MUSEUM, "Museum", "博物馆", new ItemStack(Items.GOLD_BLOCK)),
                new Category(ProfileSectionId.GARDEN, "Garden", "花园", new ItemStack(Items.FLOWERING_AZALEA)),
                new Category(ProfileSectionId.MARKET, "Market", "市场", new ItemStack(Items.EMERALD))
        );

        private CategoryHolder() {
        }
    }

    private boolean chinese() {
        return "zh_cn".equals(ConfigManager.get().language);
    }

    private String text(String english, String chinese) {
        return chinese() ? chinese : english;
    }

    private static ItemStack redLeatherChestplate() {
        ItemStack stack = new ItemStack(Items.LEATHER_CHESTPLATE);
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(0xC83A3A));
        return stack;
    }

    private record Category(ProfileSectionId section, String english, String chinese, ItemStack icon) {
        String label(boolean useChinese) {
            return useChinese ? chinese : english;
        }
    }

    private record CategoryHit(Category category, int x, int y, int width, int height) {
        boolean contains(double mouseX, double mouseY) {
            return AcaUiTheme.contains(mouseX, mouseY, x, y, width, height);
        }
    }

    private record ItemHit(ProfileItemHover hover, int x, int y, int width, int height) {
        boolean contains(double mouseX, double mouseY) {
            return AcaUiTheme.contains(mouseX, mouseY, x, y, width, height);
        }
    }

    private record Banner(String message, int color) {
    }
}
