package cloudy.autume.addition;

import cloudy.autume.addition.config.ConfigManager;
import cloudy.autume.addition.config.ConfigScreen;
import cloudy.autume.addition.config.IntegrationScanService;
import cloudy.autume.addition.config.ModConfig;
import cloudy.autume.addition.combat.DeathSaveAlertManager;
import cloudy.autume.addition.combat.DeployableExpiryAlert;
import cloudy.autume.addition.compat.MinecraftClientCompat;
import cloudy.autume.addition.fishing.FishingBiteAlert;
import cloudy.autume.addition.hud.HudRenderer;
import cloudy.autume.addition.i18n.ModText;
import cloudy.autume.addition.input.HotkeyInputs;
import cloudy.autume.addition.inventory.ItemTimestampTooltip;
import cloudy.autume.addition.inventory.CenturyCakeEffectsScreen;
import cloudy.autume.addition.inventory.CenturyCakeManager;
import cloudy.autume.addition.inventory.ShardFusionScreen;
import cloudy.autume.addition.inventory.ShardWarehouseManager;
import cloudy.autume.addition.hunting.HuntingTracker;
import cloudy.autume.addition.hunting.HuntingWorldRenderer;
import cloudy.autume.addition.inventory.SafariBeltTooltip;
import cloudy.autume.addition.party.FriendRosterStore;
import cloudy.autume.addition.party.PartyAutoAcceptManager;
import cloudy.autume.addition.party.PartyCommandEngine;
import cloudy.autume.addition.party.PrivatePartyRequestCommands;
import cloudy.autume.addition.tracker.LocationTracker;
import cloudy.autume.addition.tracker.HotmSlotTracker;
import cloudy.autume.addition.tracker.PetTracker;
import cloudy.autume.addition.tracker.PetSkinTracker;
import cloudy.autume.addition.tracker.TabListTracker;
import cloudy.autume.addition.update.ReleaseUpdateChecker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import com.mojang.blaze3d.platform.MacosUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class QCloudyAdditionClient implements ClientModInitializer {
    public static final String MOD_ID = "qcloudy_addition";
    public static final Logger LOGGER = LoggerFactory.getLogger("QCloudy_Addition");
    private static final PartyAutoAcceptManager PARTY_AUTO_ACCEPT =
            new PartyAutoAcceptManager(FriendRosterStore.createDefault());
    private static final PartyCommandEngine PARTY_COMMAND_ENGINE = new PartyCommandEngine();
    private static final PrivatePartyRequestCommands PRIVATE_PARTY_REQUESTS =
            new PrivatePartyRequestCommands();
    private static final ReleaseUpdateChecker RELEASE_UPDATES =
            ReleaseUpdateChecker.createDefault();
    private static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(MOD_ID, "controls"));
    private static final KeyMapping OPEN_CONFIG = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.qcloudy_addition.open_config", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, KEY_CATEGORY));
    private static final KeyMapping PEEK_CHAT = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.qcloudy_addition.peek_chat", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, KEY_CATEGORY));
    private static final KeyMapping OPEN_SHARD_FUSION = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.qcloudy_addition.open_shard_fusion", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN, KEY_CATEGORY));
    private static final String[] COMMAND_ALIASES = {"qca", "qc"};
    private int ticks;

    @Override
    public void onInitializeClient() {
        ConfigManager.load();
        PARTY_AUTO_ACCEPT.load();
        ShardWarehouseManager.load();
        CenturyCakeManager.load();
        ItemTimestampTooltip.register();
        SafariBeltTooltip.register();
        HuntingWorldRenderer.register();

        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (player == Minecraft.getInstance().player) {
                Minecraft client = Minecraft.getInstance();
                var stack = player.getItemInHand(hand);
                FishingBiteAlert.onRodUse(client, stack);
                DeployableExpiryAlert.onItemUse(client, stack);
            }
            return net.minecraft.world.InteractionResult.PASS;
        });

        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (player == Minecraft.getInstance().player) {
                DeployableExpiryAlert.onItemUse(Minecraft.getInstance(), player.getItemInHand(hand));
            }
            return net.minecraft.world.InteractionResult.PASS;
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ticks++;
            IntegrationScanService.tick();
            if (ticks % 20 == 0) {
                LocationTracker.update(client);
                TabListTracker.update(client);
                HuntingTracker.updateReceivedText(TabListTracker.lines(), LocationTracker.scoreboardLines());
                HotmSlotTracker.update(client);
                PetSkinTracker.update(client);
                ShardWarehouseManager.update(client);
                CenturyCakeManager.tick(client);
            }
            HuntingTracker.tick(client);
            FishingBiteAlert.tick(client);
            DeployableExpiryAlert.tick(client);
        });

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            onPartyMessage(message, overlay);
            onDeathSaveMessage(message, overlay);
            PetTracker.onChat(message.getString(), overlay);
            HuntingTracker.onMessage(message, overlay);
            DeployableExpiryAlert.onMessage(message, overlay);
            CenturyCakeManager.onMessage(message, overlay);
            if (!overlay) PetSkinTracker.onChat(message.getString());
        });
        // Compatibility path for chat compactors (for example SkyHanni): GAME
        // and GAME_CANCELED are mutually exclusive for one received message.
        ClientReceiveMessageEvents.GAME_CANCELED.register((message, overlay) -> {
            onPartyMessage(message, overlay);
            onDeathSaveMessage(message, overlay);
            HuntingTracker.onMessage(message, overlay);
            DeployableExpiryAlert.onMessage(message, overlay);
            CenturyCakeManager.onMessage(message, overlay);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            resetTrackers();
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                RELEASE_UPDATES.onJoin(client));

        HudElementRegistry.attachElementAfter(VanillaHudElements.OVERLAY_MESSAGE,
                Identifier.fromNamespaceAndPath(MOD_ID, "main_hud"), (graphics, tickCounter) -> HudRenderer.render(graphics));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            for (String alias : COMMAND_ALIASES) {
                if (dispatcher.getRoot().getChild(alias) != null) {
                    LOGGER.warn("Skipping client command /{} because another mod already registered it", alias);
                    continue;
                }
                dispatcher.register(ClientCommands.literal(alias).executes(context -> {
                    var client = context.getSource().getClient();
                    client.execute(() -> MinecraftClientCompat.setScreen(client,
                            new ConfigScreen(MinecraftClientCompat.screen(client))));
                    return 1;
                }));
            }
            if (dispatcher.getRoot().getChild("th") == null) {
                dispatcher.register(ClientCommands.literal("th").executes(context -> {
                    var connection = context.getSource().getClient().getConnection();
                    if (connection != null) connection.sendCommand("warp torrhus");
                    return 1;
                }));
            } else {
                LOGGER.warn("Skipping client command /th because another mod already registered it");
            }
            if (dispatcher.getRoot().getChild("helia") == null) {
                dispatcher.register(ClientCommands.literal("helia").executes(context -> {
                    var connection = context.getSource().getClient().getConnection();
                    if (connection != null) connection.sendCommand("chapter torrhus");
                    return 1;
                }));
            } else {
                LOGGER.warn("Skipping client command /helia because another mod already registered it");
            }
            if (dispatcher.getRoot().getChild("qshard") == null) {
                dispatcher.register(ClientCommands.literal("qshard")
                        .executes(context -> openShardFusionCommand(context.getSource(), ""))
                        .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                                .executes(context -> openShardFusionCommand(context.getSource(),
                                        StringArgumentType.getString(context, "name").trim()))));
            } else {
                LOGGER.warn("Skipping client command /qshard because another mod already registered it");
            }
            registerCenturyCakeCommand(dispatcher, "cake");
            registerCenturyCakeCommand(dispatcher, "centurycakeeffect");
            registerPartyClientCommands(dispatcher);
        });

        LOGGER.info("QCloudy_Addition initialized in client-side mode");
    }

    public static boolean matchesChord(ChordAction action, KeyEvent event) {
        return key(action).matches(event)
                && modifierMask(event.modifiers()) == modifiers(action);
    }

    public static boolean matchesBaseKey(ChordAction action, KeyEvent event) {
        return key(action).matches(event);
    }

    public static boolean matchesMouseChord(ChordAction action, MouseButtonEvent event) {
        return key(action).matchesMouse(event)
                && modifierMask(event.modifiers()) == modifiers(action);
    }

    public static boolean matchesBaseMouse(ChordAction action, MouseButtonEvent event) {
        return key(action).matchesMouse(event);
    }

    public static boolean isChordDown(ChordAction action) {
        KeyMapping mapping = key(action);
        return !mapping.isUnbound() && mapping.isDown() && activeModifierMask() == modifiers(action);
    }

    public static String chordName(ChordAction action) {
        if (key(action).isUnbound()) return cloudy.autume.addition.i18n.ModText.get("config.key.unbound");
        int modifiers = modifiers(action);
        StringBuilder result = new StringBuilder();
        appendModifier(result, modifiers, GLFW.GLFW_MOD_CONTROL, "Ctrl");
        appendModifier(result, modifiers, GLFW.GLFW_MOD_SHIFT, "Shift");
        appendModifier(result, modifiers, GLFW.GLFW_MOD_ALT, "Alt");
        appendModifier(result, modifiers, GLFW.GLFW_MOD_SUPER, MacosUtil.IS_MACOS ? "Cmd" : "Super");
        if (!result.isEmpty()) result.append('+');
        result.append(key(action).getTranslatedKeyMessage().getString());
        return result.toString();
    }

    public static void setKeyboardChord(ChordAction action, int keyCode, int modifiers) {
        setChord(action, InputConstants.Type.KEYSYM.getOrCreate(keyCode), modifiers);
    }

    public static void setMouseChord(ChordAction action, int button, int modifiers) {
        if (!HotkeyInputs.supportedMouseButton(button)) return;
        setChord(action, InputConstants.Type.MOUSE.getOrCreate(button), modifiers);
    }

    public static void clearChord(ChordAction action) {
        setChord(action, InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_UNKNOWN), 0);
    }

    public static boolean openShardFusionGuide(Minecraft client, Screen parent, String initialQuery) {
        if (!ConfigManager.get().inventory.shardFusionHelper) return false;
        MinecraftClientCompat.setScreen(client, new ShardFusionScreen(parent, initialQuery));
        return true;
    }

    private static int openShardFusionCommand(FabricClientCommandSource source, String query) {
        if (!ConfigManager.get().inventory.shardFusionHelper) {
            source.sendError(ModText.component("shard.disabled"));
            return 0;
        }
        Minecraft client = source.getClient();
        client.execute(() -> openShardFusionGuide(client, MinecraftClientCompat.screen(client), query));
        return 1;
    }

    private static void registerCenturyCakeCommand(
            com.mojang.brigadier.CommandDispatcher<FabricClientCommandSource> dispatcher, String name) {
        if (dispatcher.getRoot().getChild(name) != null) {
            LOGGER.warn("Skipping client command /{} because another mod already registered it", name);
            return;
        }
        dispatcher.register(ClientCommands.literal(name).executes(context -> {
            Minecraft client = context.getSource().getClient();
            client.execute(() -> MinecraftClientCompat.setScreen(client,
                    new CenturyCakeEffectsScreen(MinecraftClientCompat.screen(client))));
            return 1;
        }));
    }

    /**
     * Registers roots whose literal begins with {@code /}. Minecraft removes
     * the first slash before Fabric parses a command, so these roots are
     * reached only from the documented double-slash forms such as
     * {@code //m7}; the normal Hypixel {@code /m7} namespace is untouched.
     */
    private static void registerPartyClientCommands(
            CommandDispatcher<FabricClientCommandSource> dispatcher) {
        registerLocalNoArg(dispatcher, "/warp", "//warp");
        registerLocalNoArg(dispatcher, "/w", "//w");
        registerLocalNoArg(dispatcher, "/allinvite", "//allinvite");
        registerLocalNoArg(dispatcher, "/all", "//all");
        registerLocalNoArg(dispatcher, "/allinv", "//allinv");

        registerLocalTarget(dispatcher, "/pt", "//pt", PartyCommandEngine.Feature.TRANSFER);
        registerLocalNoArg(dispatcher, "/ptme", "//ptme");
        registerLocalTarget(dispatcher, "/k", "//k", PartyCommandEngine.Feature.KICK);
        registerLocalNoArg(dispatcher, "/sc", "//sc");
        registerLocalNoArg(dispatcher, "/sendcoords", "//sendcoords");
        registerLocalNoArg(dispatcher, "/c", "//c");
        registerLocalTarget(dispatcher, "/pp", "//pp", PartyCommandEngine.Feature.PROMOTE);
        registerLocalWord(dispatcher, "/stream", "//stream");
        registerLocalWord(dispatcher, "/st", "//st");
        registerLocalWord(dispatcher, "/s", "//s");

        for (String alias : List.of("fe", "f0", "me", "m0",
                "f1", "f2", "f3", "f4", "f5", "f6", "f7",
                "m1", "m2", "m3", "m4", "m5", "m6", "m7",
                "t1", "t2", "t3", "t4", "t5")) {
            registerLocalNoArg(dispatcher, "/" + alias, "//" + alias);
        }

        registerQuickPrivateCommands(dispatcher);
        registerPartyChatSuggestions(dispatcher);
    }

    private static void registerLocalNoArg(CommandDispatcher<FabricClientCommandSource> dispatcher,
                                           String root, String input) {
        if (!availableClientRoot(dispatcher, root)) return;
        dispatcher.register(ClientCommands.literal(root)
                .executes(context -> executeLocalPartyCommand(context.getSource(), input)));
    }

    private static void registerLocalTarget(CommandDispatcher<FabricClientCommandSource> dispatcher,
                                            String root, String input,
                                            PartyCommandEngine.Feature feature) {
        if (!availableClientRoot(dispatcher, root)) return;
        LiteralArgumentBuilder<FabricClientCommandSource> command = ClientCommands.literal(root)
                .executes(context -> executeLocalPartyCommand(context.getSource(), input));
        command.then(ClientCommands.argument("player", StringArgumentType.word())
                .suggests((context, builder) -> suggestPartyPlayers(context, builder, feature))
                .executes(context -> executeLocalPartyCommand(context.getSource(),
                        input + " " + StringArgumentType.getString(context, "player"))));
        dispatcher.register(command);
    }

    private static void registerLocalWord(CommandDispatcher<FabricClientCommandSource> dispatcher,
                                          String root, String input) {
        if (!availableClientRoot(dispatcher, root)) return;
        dispatcher.register(ClientCommands.literal(root)
                .executes(context -> executeLocalPartyCommand(context.getSource(), input))
                .then(ClientCommands.argument("value", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            String remaining = builder.getRemainingLowerCase();
                            for (String value : List.of("c", "close", "off")) {
                                if (value.startsWith(remaining)) builder.suggest(value);
                            }
                            return builder.buildFuture();
                        })
                        .executes(context -> executeLocalPartyCommand(context.getSource(),
                                input + " " + StringArgumentType.getString(context, "value")))));
    }

    private static void registerQuickPrivateCommands(
            CommandDispatcher<FabricClientCommandSource> dispatcher) {
        if (availableClientRoot(dispatcher, "/i")) {
            dispatcher.register(ClientCommands.literal("/i")
                    .executes(context -> executeQuickPrivateCommand(context.getSource(), "//i"))
                    .then(ClientCommands.argument("player", StringArgumentType.word())
                            .suggests(QCloudyAdditionClient::suggestAllPartyPlayers)
                            .executes(context -> executeQuickPrivateCommand(context.getSource(),
                                    "//i " + StringArgumentType.getString(context, "player")))));
        }
        if (availableClientRoot(dispatcher, "/invited")) {
            dispatcher.register(ClientCommands.literal("/invited")
                    .executes(context -> executeQuickPrivateCommand(context.getSource(), "//invited"))
                    .then(ClientCommands.argument("player", StringArgumentType.word())
                            .suggests(QCloudyAdditionClient::suggestAllPartyPlayers)
                            .executes(context -> executeQuickPrivateCommand(context.getSource(),
                                    "//invited " + StringArgumentType.getString(context, "player"))))
                    .then(ClientCommands.literal("by")
                            .executes(context -> executeQuickPrivateCommand(
                                    context.getSource(), "//invited by"))
                            .then(ClientCommands.argument("player", StringArgumentType.word())
                                    .suggests(QCloudyAdditionClient::suggestAllPartyPlayers)
                                    .executes(context -> executeQuickPrivateCommand(context.getSource(),
                                            "//invited by "
                                                    + StringArgumentType.getString(context, "player"))))));
        }
    }

    /** Suggests QCA party-chat verbs without claiming or executing /pc. */
    private static void registerPartyChatSuggestions(
            CommandDispatcher<FabricClientCommandSource> dispatcher) {
        if (!availableClientRoot(dispatcher, "pc")) return;
        dispatcher.register(ClientCommands.literal("pc")
                .then(ClientCommands.argument("qca_party_message", StringArgumentType.greedyString())
                        .suggests(QCloudyAdditionClient::suggestPartyChatCommand)));
    }

    private static boolean availableClientRoot(CommandDispatcher<FabricClientCommandSource> dispatcher,
                                               String root) {
        if (dispatcher.getRoot().getChild(root) == null) return true;
        LOGGER.warn("Skipping client command root {} because another mod already registered it", root);
        return false;
    }

    private static int executeLocalPartyCommand(FabricClientCommandSource source, String input) {
        Minecraft client = source.getClient();
        if (!isOnHypixel(client)) {
            source.sendError(ModText.component("party.command.hypixel_only"));
            return 0;
        }
        ModConfig.Chat chat = ConfigManager.get().chat;
        PartyCommandEngine.Result result = PARTY_COMMAND_ENGINE.handleLocalDoubleSlash(
                input, localPlayerName(client), chat.partyCommands,
                feature -> localPartyFeatureEnabled(chat, feature), playerCoordinates(client),
                System.nanoTime());
        return handlePartyCommandResult(client, result, true) ? 1 : 0;
    }

    private static int executeQuickPrivateCommand(FabricClientCommandSource source, String input) {
        Minecraft client = source.getClient();
        if (!isOnHypixel(client)) {
            source.sendError(ModText.component("party.command.hypixel_only"));
            return 0;
        }
        if (!ConfigManager.get().chat.quickPrivatePartyRequest) {
            source.sendError(ModText.component("party.command.disabled"));
            return 0;
        }
        PrivatePartyRequestCommands.LocalResult result =
                PrivatePartyRequestCommands.fromLocalDoubleSlash(input);
        if (result.status() == PrivatePartyRequestCommands.Status.COMMAND) {
            sendServerCommand(client, result.payload());
            return 1;
        }
        source.sendError(ModText.component("party.command.invalid_arguments"));
        return 0;
    }

    private static CompletableFuture<Suggestions> suggestPartyPlayers(
            CommandContext<FabricClientCommandSource> context, SuggestionsBuilder builder,
            PartyCommandEngine.Feature feature) {
        ModConfig.Chat chat = ConfigManager.get().chat;
        if (!chat.partyCommands || !localPartyFeatureEnabled(chat, feature)) {
            return builder.buildFuture();
        }
        return appendPartyPlayerSuggestions(context, builder);
    }

    private static CompletableFuture<Suggestions> suggestAllPartyPlayers(
            CommandContext<FabricClientCommandSource> context, SuggestionsBuilder builder) {
        return appendPartyPlayerSuggestions(context, builder);
    }

    private static CompletableFuture<Suggestions> appendPartyPlayerSuggestions(
            CommandContext<FabricClientCommandSource> context, SuggestionsBuilder builder) {
        Minecraft client = context.getSource().getClient();
        for (String player : PARTY_COMMAND_ENGINE.suggestPlayers(
                builder.getRemaining(), localPlayerName(client))) {
            builder.suggest(player);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestPartyChatCommand(
            CommandContext<FabricClientCommandSource> context, SuggestionsBuilder builder) {
        ModConfig.Chat chat = ConfigManager.get().chat;
        if (!chat.fastPartyCommands) return builder.buildFuture();
        String input = builder.getRemaining();
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        int split = normalized.indexOf(' ');
        if (split < 0) {
            for (String suggestion : PARTY_COMMAND_ENGINE.suggestCommands(
                    normalized, PartyCommandEngine.EntryPoint.PARTY_CHAT, true,
                    feature -> fastPartyFeatureEnabled(chat, feature))) {
                builder.suggest(suggestion);
            }
            return builder.buildFuture();
        }

        String verb = normalized.substring(0, split);
        String fragment = normalized.substring(split + 1).trim();
        PartyCommandEngine.Feature targetFeature = switch (verb) {
            case "!pt" -> PartyCommandEngine.Feature.TRANSFER;
            case "!k" -> PartyCommandEngine.Feature.KICK;
            case "!pp" -> PartyCommandEngine.Feature.PROMOTE;
            default -> null;
        };
        if (targetFeature != null && fastPartyFeatureEnabled(chat, targetFeature)
                && fragment.indexOf(' ') < 0) {
            for (String player : PARTY_COMMAND_ENGINE.suggestPlayers(
                    fragment, localPlayerName(context.getSource().getClient()))) {
                builder.suggest(verb + " " + player);
            }
        } else if (fastPartyFeatureEnabled(chat, PartyCommandEngine.Feature.STREAM)
                && (verb.equals("!stream") || verb.equals("!st") || verb.equals("!s"))) {
            for (String value : List.of("c", "close", "off")) {
                if (value.startsWith(fragment)) builder.suggest(verb + " " + value);
            }
        }
        return builder.buildFuture();
    }

    private static void setChord(ChordAction action, InputConstants.Key input, int modifiers) {
        key(action).setKey(input);
        setModifiers(action, modifierMask(modifiers));
        KeyMapping.resetMapping();
        ConfigManager.save();
    }

    private static KeyMapping key(ChordAction action) {
        return switch (action) {
            case OPEN_CONFIG -> OPEN_CONFIG;
            case PEEK_CHAT -> PEEK_CHAT;
            case OPEN_SHARD_FUSION -> OPEN_SHARD_FUSION;
        };
    }

    private static int modifiers(ChordAction action) {
        var keybinds = ConfigManager.get().keybinds;
        return switch (action) {
            case OPEN_CONFIG -> keybinds.openConfigModifiers;
            case PEEK_CHAT -> keybinds.peekChatModifiers;
            case OPEN_SHARD_FUSION -> keybinds.openShardFusionModifiers;
        };
    }

    private static void setModifiers(ChordAction action, int modifiers) {
        var keybinds = ConfigManager.get().keybinds;
        switch (action) {
            case OPEN_CONFIG -> keybinds.openConfigModifiers = modifiers;
            case PEEK_CHAT -> keybinds.peekChatModifiers = modifiers;
            case OPEN_SHARD_FUSION -> keybinds.openShardFusionModifiers = modifiers;
        }
    }

    private static int modifierMask(int modifiers) {
        return modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SHIFT
                | GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER);
    }

    private static int activeModifierMask() {
        long window = net.minecraft.client.Minecraft.getInstance().getWindow().handle();
        int result = 0;
        if (pressed(window, GLFW.GLFW_KEY_LEFT_CONTROL) || pressed(window, GLFW.GLFW_KEY_RIGHT_CONTROL)) {
            result |= GLFW.GLFW_MOD_CONTROL;
        }
        if (pressed(window, GLFW.GLFW_KEY_LEFT_SHIFT) || pressed(window, GLFW.GLFW_KEY_RIGHT_SHIFT)) {
            result |= GLFW.GLFW_MOD_SHIFT;
        }
        if (pressed(window, GLFW.GLFW_KEY_LEFT_ALT) || pressed(window, GLFW.GLFW_KEY_RIGHT_ALT)) {
            result |= GLFW.GLFW_MOD_ALT;
        }
        if (pressed(window, GLFW.GLFW_KEY_LEFT_SUPER) || pressed(window, GLFW.GLFW_KEY_RIGHT_SUPER)) {
            result |= GLFW.GLFW_MOD_SUPER;
        }
        return result;
    }

    private static boolean pressed(long window, int key) {
        return GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
    }

    private static void appendModifier(StringBuilder result, int value, int flag, String label) {
        if ((value & flag) == 0) return;
        if (!result.isEmpty()) result.append('+');
        result.append(label);
    }

    public enum ChordAction {
        OPEN_CONFIG,
        PEEK_CHAT,
        OPEN_SHARD_FUSION
    }

    private static void resetTrackers() {
        LocationTracker.reset();
        TabListTracker.reset();
        PetTracker.reset();
        PetSkinTracker.reset();
        HuntingTracker.reset();
        FishingBiteAlert.reset();
        DeployableExpiryAlert.reset();
        DeathSaveAlertManager.resetRuntime();
        PARTY_AUTO_ACCEPT.resetSession();
        PARTY_COMMAND_ENGINE.resetSession();
        PRIVATE_PARTY_REQUESTS.resetSession();
    }

    private static void onPartyMessage(Component message, boolean overlay) {
        if (message == null || overlay) return;
        Minecraft client = Minecraft.getInstance();
        var server = client.getCurrentServer();
        String serverAddress = server == null ? "" : server.ip;
        boolean onHypixel = PartyAutoAcceptManager.isHypixelAddress(serverAddress);
        var chat = ConfigManager.get().chat;
        String command = PARTY_AUTO_ACCEPT.onMessage(message, overlay,
                onHypixel,
                client.getUser().getProfileId().toString(), chat.partyAutoAccept,
                chat.partyAutoAcceptFriendMode, chat.partyAutoAcceptWhitelist,
                System.currentTimeMillis());
        if (command != null) sendServerCommand(client, command);
        if (!onHypixel) return;

        if (chat.directMessagePartyRequest) {
            PRIVATE_PARTY_REQUESTS.handleIncomingDirectMessage(message.getString(), System.nanoTime())
                    .ifPresent(payload -> sendServerCommand(client, payload));
        }

        PartyCommandEngine.Result result = PARTY_COMMAND_ENGINE.handlePartyChat(
                message.getString(), localPlayerName(client), chat.fastPartyCommands,
                feature -> fastPartyFeatureEnabled(chat, feature),
                feature -> fastPartyTrigger(chat, feature), playerCoordinates(client),
                System.nanoTime());
        handlePartyCommandResult(client, result, false);
    }

    private static boolean handlePartyCommandResult(Minecraft client,
                                                    PartyCommandEngine.Result result,
                                                    boolean localInput) {
        if (result == null || result.status() == PartyCommandEngine.Status.IGNORED) return false;
        if (result.status() == PartyCommandEngine.Status.COMMAND) {
            sendServerCommand(client, result.payload());
            return true;
        }

        if (client.player == null) return false;
        Component feedback;
        if (result.status() == PartyCommandEngine.Status.DISABLED) {
            feedback = ModText.component("party.command.disabled");
        } else if (result.status() == PartyCommandEngine.Status.COOLDOWN) {
            long seconds = Math.max(1L,
                    (result.retryAfterNanos() + 999_999_999L) / 1_000_000_000L);
            feedback = ModText.component("party.command.cooldown", seconds);
        } else {
            feedback = switch (result.error()) {
                case INVALID_ARGUMENTS -> ModText.component("party.command.invalid_arguments");
                case INVALID_PLAYER -> ModText.component("party.command.invalid_player");
                case AMBIGUOUS_PLAYER -> ModText.component("party.command.ambiguous_player",
                        String.join(", ", result.candidates()));
                case COORDINATES_UNAVAILABLE -> ModText.component("party.command.coordinates_unavailable");
                case NONE -> ModText.component("party.command.invalid_arguments");
            };
        }
        client.player.sendSystemMessage(Component.literal("[QCA] ")
                .withStyle(ChatFormatting.AQUA).append(feedback.copy().withStyle(ChatFormatting.RED)));
        return localInput;
    }

    private static void sendServerCommand(Minecraft client, String payload) {
        if (payload == null || payload.isBlank()) return;
        var connection = client.getConnection();
        if (connection != null) connection.sendCommand(payload);
    }

    private static boolean isOnHypixel(Minecraft client) {
        var server = client.getCurrentServer();
        return server != null && PartyAutoAcceptManager.isHypixelAddress(server.ip);
    }

    private static String localPlayerName(Minecraft client) {
        return client.getUser().getName();
    }

    private static PartyCommandEngine.BlockCoordinates playerCoordinates(Minecraft client) {
        if (client.player == null) return null;
        var position = client.player.blockPosition();
        return new PartyCommandEngine.BlockCoordinates(
                position.getX(), position.getY(), position.getZ());
    }

    private static boolean fastPartyFeatureEnabled(ModConfig.Chat chat,
                                                   PartyCommandEngine.Feature feature) {
        return switch (feature) {
            case WARP -> chat.fastPartyWarp;
            case ALL_INVITE -> chat.fastPartyAllInvite;
            case TRANSFER -> chat.fastPartyTransfer;
            case KICK -> chat.fastPartyKick;
            case COORDINATES -> chat.fastPartyCoordinates;
            case PROMOTE -> chat.fastPartyPromote;
            case STREAM -> chat.fastPartyStream;
            case DUNGEON -> chat.fastPartyDungeon;
            case KUUDRA -> chat.fastPartyKuudra;
        };
    }

    private static boolean localPartyFeatureEnabled(ModConfig.Chat chat,
                                                    PartyCommandEngine.Feature feature) {
        return switch (feature) {
            case WARP -> chat.partyCommandWarp;
            case ALL_INVITE -> chat.partyCommandAllInvite;
            case TRANSFER -> chat.partyCommandTransfer;
            case KICK -> chat.partyCommandKick;
            case COORDINATES -> chat.partyCommandCoordinates;
            case PROMOTE -> chat.partyCommandPromote;
            case STREAM -> chat.partyCommandStream;
            case DUNGEON -> chat.partyCommandDungeon;
            case KUUDRA -> chat.partyCommandKuudra;
        };
    }

    private static PartyCommandEngine.TriggerScope fastPartyTrigger(
            ModConfig.Chat chat, PartyCommandEngine.Feature feature) {
        ModConfig.PartyCommandTrigger trigger = switch (feature) {
            case WARP -> chat.fastPartyWarpTrigger;
            case ALL_INVITE -> chat.fastPartyAllInviteTrigger;
            case TRANSFER -> chat.fastPartyTransferTrigger;
            case KICK -> chat.fastPartyKickTrigger;
            case COORDINATES -> chat.fastPartyCoordinatesTrigger;
            case PROMOTE -> chat.fastPartyPromoteTrigger;
            case STREAM -> chat.fastPartyStreamTrigger;
            case DUNGEON -> chat.fastPartyDungeonTrigger;
            case KUUDRA -> chat.fastPartyKuudraTrigger;
        };
        return switch (trigger) {
            case SELF_ONLY -> PartyCommandEngine.TriggerScope.SELF_ONLY;
            case OTHERS_ONLY -> PartyCommandEngine.TriggerScope.OTHERS_ONLY;
            case EVERYONE -> PartyCommandEngine.TriggerScope.EVERYONE;
        };
    }

    private static void onDeathSaveMessage(Component message, boolean overlay) {
        if (overlay || message == null) return;
        DeathSaveAlertManager.Alert alert = DeathSaveAlertManager.handle(message.getString());
        if (alert == null) return;

        // The three cooldown HUD switches are independent from the center-title
        // switch. Always accept the server-confirmed activation above so a HUD
        // can keep counting even when the player has disabled the large title.
        if (!ConfigManager.get().combat.deathSaveAlerts) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        MinecraftClientCompat.showTitle(client,
                Component.literal(alert.centerTitle()).withStyle(ChatFormatting.BOLD, ChatFormatting.RED),
                Component.empty(), 6, 42, 10);
    }
}
