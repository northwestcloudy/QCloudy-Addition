package cloudy.autume.addition.profile.ui;

import cloudy.autume.addition.profile.ProfileDescriptor;
import cloudy.autume.addition.profile.ProfileSection;
import cloudy.autume.addition.profile.ProfileSectionId;
import cloudy.autume.addition.profile.ProfileSnapshot;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Converts the schema-v1 transport snapshot into bounded, player-facing UI models. */
final class ProfilePresentationMapper {
    private static final int MAX_STATS = 24;
    private static final int MAX_LIST_ROWS = 36;
    private static final int MAX_ITEMS_PER_GRID = 54;
    private static final double[] SKILL_XP = {
            50, 125, 200, 300, 500, 750, 1_000, 1_500, 2_000, 3_500,
            5_000, 7_500, 10_000, 15_000, 20_000, 30_000, 50_000, 75_000,
            100_000, 200_000, 300_000, 400_000, 500_000, 600_000, 700_000,
            800_000, 900_000, 1_000_000, 1_000_000, 1_000_000, 1_000_000,
            1_000_000, 1_000_000, 1_000_000, 1_000_000, 1_000_000, 1_000_000,
            1_000_000, 1_000_000, 1_000_000, 1_000_000, 1_000_000, 1_000_000,
            1_000_000, 1_000_000, 1_000_000, 1_000_000, 1_000_000, 1_000_000,
            1_000_000, 1_000_000, 1_000_000, 1_000_000, 1_000_000, 1_000_000,
            1_000_000, 1_000_000, 1_000_000, 1_000_000, 1_000_000
    };
    private static final Set<String> MAIN_SKILLS = Set.of(
            "FARMING", "MINING", "COMBAT", "FORAGING", "FISHING",
            "ENCHANTING", "ALCHEMY", "TAMING", "CARPENTRY");

    private ProfilePresentationMapper() {
    }

    static HeaderView header(ProfileSnapshot snapshot, boolean chinese) {
        ProfileDescriptor selected = selected(snapshot);
        JsonObject overview = payload(snapshot, ProfileSectionId.OVERVIEW);
        JsonObject player = object(overview, "player");
        String rank = firstString(player, "newPackageRank", "new_package_rank", "rank");
        String mode = selected == null ? "" : selected.gameMode();
        return new HeaderView(snapshot.identity().name(), displayRank(rank),
                selected == null ? t(chinese, "No profile", "无档案") : selected.cuteName(),
                humanize(mode), selected == null ? 0 : selected.memberCount());
    }

    static SectionView section(ProfileSnapshot snapshot, ProfileSectionId sectionId, boolean chinese) {
        ProfileSection section = snapshot.section(sectionId).orElse(null);
        String title = sectionTitle(sectionId, chinese);
        String icon = sectionIcon(sectionId);
        if (section == null) {
            return empty(title, icon, t(chinese,
                    "This section was not returned by the profile service.",
                    "档案服务没有返回这个分类。"));
        }
        JsonObject payload = section.payload();
        if (rootProjectionLimited(payload)) {
            return empty(title, icon, t(chinese,
                    "Some data was omitted while preparing this profile. Try refreshing later.",
                    "准备档案时部分数据被省略，请稍后刷新。"));
        }
        List<Block> blocks = switch (sectionId) {
            case OVERVIEW -> overview(snapshot, payload, chinese);
            case GEAR -> gear(payload, chinese);
            case ACCESSORIES -> accessories(payload, chinese);
            case PETS -> pets(payload, chinese);
            case INVENTORY -> inventory(payload, chinese);
            case SKILLS -> skills(payload, chinese);
            case SLAYER -> slayer(payload, chinese);
            case MINIONS -> minions(payload, chinese);
            case BESTIARY -> bestiary(payload, chinese);
            case COLLECTIONS -> collections(payload, chinese);
            case MINING -> mining(payload, chinese);
            case CRIMSON_ISLE -> crimson(payload, chinese);
            case RIFT -> rift(payload, chinese);
            case MISC -> misc(payload, chinese);
            case MUSEUM -> museum(payload, chinese);
            case GARDEN -> garden(payload, chinese);
            case MARKET -> market(payload, chinese);
        };
        boolean partiallyLimited = containsProjectionLimit(payload);
        if (blocks.isEmpty() && ProfileDebugFallback.enabled()) {
            blocks = ProfileDebugFallback.blocks(payload, chinese);
        }
        if (blocks.isEmpty()) {
            return empty(title, icon, t(chinese,
                    "No displayable data is available for this section.",
                    "此分类暂无可显示的数据。"));
        }
        if (partiallyLimited) {
            blocks = new ArrayList<>(blocks);
            blocks.add(new NoticeBlock(t(chinese, "Partial data", "部分数据"),
                    t(chinese,
                            "Some entries were omitted while keeping this profile response safe.",
                            "为保证档案响应安全，部分条目已被省略。")));
        }
        return new SectionView(title, icon, List.copyOf(blocks));
    }

    private static List<Block> overview(ProfileSnapshot snapshot, JsonObject root, boolean chinese) {
        List<Block> blocks = new ArrayList<>();
        JsonObject profile = object(root, "profile");
        JsonObject banking = object(profile, "banking");
        JsonObject currencies = object(root, "currencies");
        JsonObject leveling = object(root, "leveling");
        JsonObject playerData = object(root, "playerData", "player_data");
        JsonObject market = payload(snapshot, ProfileSectionId.MARKET);
        JsonObject coinBalances = object(market, "coinBalances", "coin_balances");
        List<StatCard> cards = new ArrayList<>();

        Double skyBlockXp = firstNumber(leveling, "experience", "skyblock_experience");
        if (skyBlockXp != null) {
            double level = Math.max(0.0, skyBlockXp / 100.0);
            cards.add(new StatCard(t(chinese, "SkyBlock Level", "SkyBlock 等级"),
                    decimal(level, 1), StatTone.ACCENT));
            blocks.add(new ProgressBlock(t(chinese, "SkyBlock Progress", "SkyBlock 进度"),
                    List.of(new ProgressEntry(t(chinese, "SkyBlock Level", "SkyBlock 等级"),
                            "Lv " + (int) Math.floor(level), formatNumber(skyBlockXp) + " XP",
                            level - Math.floor(level)))));
        }
        Double purse = firstNumber(currencies, "coin_purse", "coins");
        if (purse == null) purse = firstNumber(coinBalances, "purse");
        addCoin(cards, t(chinese, "Purse", "钱包"), purse);
        Double bank = firstNumber(banking, "balance");
        if (bank == null) bank = firstNumber(coinBalances, "bank");
        addCoin(cards, t(chinese, "Bank", "银行"), bank);
        Double netWorth = firstNumber(market, "knownEstimatedValue", "estimatedNetWorth");
        if (netWorth != null) {
            boolean complete = bool(market, "estimateComplete");
            cards.add(new StatCard(t(chinese,
                    complete ? "Net Worth" : "Known Value",
                    complete ? "净资产" : "已知估值"), coins(netWorth),
                    complete ? StatTone.SUCCESS : StatTone.WARNING));
        }
        ProfileDescriptor selected = selected(snapshot);
        if (selected != null) {
            cards.add(new StatCard(t(chinese, "Profile", "档案"), selected.cuteName(), StatTone.NORMAL));
            cards.add(new StatCard(t(chinese, "Members", "成员"),
                    Integer.toString(selected.memberCount()), StatTone.NORMAL));
            if (!selected.gameMode().isBlank()) {
                cards.add(new StatCard(t(chinese, "Mode", "模式"),
                        humanize(selected.gameMode()), StatTone.NORMAL));
            }
        }
        addNumber(cards, t(chinese, "Fairy Souls", "仙女之魂"),
                firstNumber(playerData, "fairy_souls_collected"));
        if (!cards.isEmpty()) blocks.addFirst(new StatGridBlock("", List.copyOf(cards)));
        return blocks;
    }

    private static List<Block> gear(JsonObject root, boolean chinese) {
        List<Block> result = new ArrayList<>();
        addArmorContainer(result, root, t(chinese, "Armor", "盔甲"), chinese,
                "inv_armor", "armor");
        addContainer(result, root, t(chinese, "Equipment", "装备栏"), 4, 4,
                chinese, "equipment_contents", "equipment");
        addContainer(result, root, t(chinese, "Wardrobe", "衣柜"), 9, 36,
                chinese, "wardrobe_contents", "wardrobe");
        return result;
    }

    private static List<Block> accessories(JsonObject root, boolean chinese) {
        List<Block> result = new ArrayList<>();
        JsonObject storage = object(root, "accessoryBagStorage", "accessory_bag_storage");
        List<StatCard> summary = new ArrayList<>();
        addNumber(summary, t(chinese, "Magical Power", "魔法力量"),
                firstNumber(storage, "highest_magical_power", "magical_power"));
        String power = firstString(storage, "selected_power", "selectedPower");
        if (!power.isBlank()) summary.add(new StatCard(t(chinese, "Power", "饰品之力"),
                humanize(power), StatTone.ACCENT));
        addNumber(summary, t(chinese, "Bag Upgrades", "饰品袋升级"),
                firstNumber(storage, "bag_upgrades_purchased"));
        if (!summary.isEmpty()) result.add(new StatGridBlock("", List.copyOf(summary)));

        JsonObject tuning = object(storage, "tuning");
        JsonObject current = object(tuning, "slot_0", "selected");
        List<StatCard> tuningCards = statWhitelist(current, chinese, Map.ofEntries(
                Map.entry("health", t(chinese, "Health", "生命")),
                Map.entry("defense", t(chinese, "Defense", "防御")),
                Map.entry("strength", t(chinese, "Strength", "力量")),
                Map.entry("intelligence", t(chinese, "Intelligence", "智力")),
                Map.entry("critical_damage", t(chinese, "Crit Damage", "暴击伤害")),
                Map.entry("critical_chance", t(chinese, "Crit Chance", "暴击率")),
                Map.entry("attack_speed", t(chinese, "Attack Speed", "攻击速度")),
                Map.entry("walk_speed", t(chinese, "Walk Speed", "移动速度"))));
        if (!tuningCards.isEmpty()) result.add(new StatGridBlock(
                t(chinese, "Current Tuning", "当前调谐分配"), tuningCards));

        JsonObject bags = object(root, "bag_contents");
        JsonObject talisman = firstObject(bags, "talisman_bag", "accessory_bag");
        if (talisman.isEmpty()) talisman = firstObject(root, "talisman_bag");
        addItemGrid(result, t(chinese, "Accessory Bag", "饰品袋"), talisman, 9, 45, chinese);
        return result;
    }

    private static List<Block> pets(JsonObject root, boolean chinese) {
        JsonArray pets = array(root, "pets");
        List<PetView> views = new ArrayList<>();
        for (JsonElement element : pets) {
            if (!element.isJsonObject() || views.size() >= 40) continue;
            JsonObject pet = element.getAsJsonObject();
            String type = firstString(pet, "type");
            if (type.isBlank()) continue;
            views.add(new PetView(humanize(type), humanize(firstString(pet, "tier")),
                    numberOrZero(pet, "exp"), bool(pet, "active"),
                    humanize(firstString(pet, "heldItem", "held_item"))));
        }
        if (views.isEmpty()) return List.of();
        views.sort(Comparator.comparing(PetView::active).reversed()
                .thenComparing(PetView::name));
        return List.of(new PetGridBlock(t(chinese, "Pets", "宠物"), List.copyOf(views)));
    }

    private static List<Block> inventory(JsonObject root, boolean chinese) {
        List<Block> result = new ArrayList<>();
        addContainer(result, root, t(chinese, "Inventory", "背包"), 9, 36, "inv_contents");
        addContainer(result, root, t(chinese, "Ender Chest", "末影箱"), 9, 45,
                chinese, "ender_chest_contents");
        addContainer(result, root, t(chinese, "Personal Vault", "个人金库"), 9, 27,
                chinese, "personal_vault_contents");
        JsonObject bags = object(root, "bag_contents");
        addContainer(result, bags, t(chinese, "Sacks", "收纳袋"), 9, 27, chinese, "sacks_bag");
        addContainer(result, bags, t(chinese, "Potion Bag", "药水袋"), 9, 27, chinese, "potion_bag");
        addContainer(result, bags, t(chinese, "Fishing Bag", "钓鱼袋"), 9, 27, chinese, "fishing_bag");
        addContainer(result, bags, t(chinese, "Quiver", "箭袋"), 9, 27, chinese, "quiver");
        JsonObject backpacks = object(root, "backpack_contents");
        int backpack = 1;
        for (Map.Entry<String, JsonElement> entry : backpacks.entrySet()) {
            if (backpack > 6 || !entry.getValue().isJsonObject()) continue;
            addItemGrid(result, t(chinese, "Backpack ", "背包 ") + backpack,
                    entry.getValue().getAsJsonObject(), 9, 27, chinese);
            backpack++;
        }
        return result;
    }

    private static List<Block> skills(JsonObject root, boolean chinese) {
        JsonObject experience = object(object(root, "playerData", "player_data"), "experience");
        if (experience.isEmpty()) experience = object(root, "experience");
        List<ProgressEntry> entries = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : experience.entrySet()) {
            String name = entry.getKey().toUpperCase(Locale.ROOT).replace("SKILL_", "");
            Double xp = number(entry.getValue());
            if (xp == null || !MAIN_SKILLS.contains(name)) continue;
            SkillProgress progress = skillProgress(xp);
            entries.add(new ProgressEntry(humanize(name), "Lv " + progress.level(),
                    formatNumber(xp) + " XP", progress.progress()));
        }
        entries.sort(Comparator.comparing(ProgressEntry::label));
        return entries.isEmpty() ? List.of() : List.of(new ProgressBlock(
                t(chinese, "Skill Progress", "技能进度"), List.copyOf(entries)));
    }

    private static List<Block> slayer(JsonObject root, boolean chinese) {
        JsonObject bosses = object(root, "slayer_bosses");
        if (bosses.isEmpty()) bosses = root;
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("zombie", t(chinese, "Revenant Horror", "僵尸 Slayer"));
        labels.put("spider", t(chinese, "Tarantula Broodfather", "蜘蛛 Slayer"));
        labels.put("wolf", t(chinese, "Sven Packmaster", "狼 Slayer"));
        labels.put("enderman", t(chinese, "Voidgloom Seraph", "末影人 Slayer"));
        labels.put("blaze", t(chinese, "Inferno Demonlord", "烈焰人 Slayer"));
        labels.put("vampire", t(chinese, "Riftstalker Bloodfiend", "吸血鬼 Slayer"));
        List<StatCard> cards = new ArrayList<>();
        for (Map.Entry<String, String> label : labels.entrySet()) {
            JsonObject boss = object(bosses, label.getKey());
            Double xp = firstNumber(boss, "xp");
            if (xp == null) continue;
            long kills = 0;
            for (int tier = 0; tier <= 5; tier++) {
                Double value = firstNumber(boss, "boss_kills_tier_" + tier);
                if (value != null) kills += Math.max(0, value.longValue());
            }
            cards.add(new StatCard(label.getValue(), formatNumber(xp) + " XP · "
                    + formatNumber(kills) + " " + t(chinese, "kills", "击杀"), StatTone.NORMAL));
        }
        return cards.isEmpty() ? List.of() : List.of(new StatGridBlock(
                t(chinese, "Slayer", "Slayer"), List.copyOf(cards)));
    }

    private static List<Block> minions(JsonObject root, boolean chinese) {
        JsonArray values = array(root, "craftedGenerators", "crafted_generators");
        Map<String, Integer> highest = new HashMap<>();
        for (JsonElement element : values) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) continue;
            String value = element.getAsString();
            int separator = value.lastIndexOf('_');
            if (separator <= 0 || separator >= value.length() - 1) continue;
            try {
                highest.merge(value.substring(0, separator), Integer.parseInt(value.substring(separator + 1)), Math::max);
            } catch (NumberFormatException ignored) {
            }
        }
        List<String> rows = highest.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .limit(MAX_LIST_ROWS)
                .map(entry -> humanize(entry.getKey()) + " · Tier " + entry.getValue())
                .toList();
        return rows.isEmpty() ? List.of() : List.of(new ListBlock(
                t(chinese, "Crafted Minions", "已制作 Minion"), rows));
    }

    private static List<Block> bestiary(JsonObject root, boolean chinese) {
        JsonObject kills = object(root, "kills");
        List<StatCard> cards = topNumberCards(kills, MAX_STATS);
        return cards.isEmpty() ? List.of() : List.of(new StatGridBlock(
                t(chinese, "Top Bestiary Kills", "生物图鉴击杀"), cards));
    }

    private static List<Block> collections(JsonObject root, boolean chinese) {
        JsonObject collection = object(root, "collection");
        List<StatCard> cards = topNumberCards(collection, MAX_STATS);
        return cards.isEmpty() ? List.of() : List.of(new StatGridBlock(
                t(chinese, "Collections", "收藏"), cards));
    }

    private static List<Block> mining(JsonObject root, boolean chinese) {
        List<Block> result = new ArrayList<>();
        List<StatCard> cards = statWhitelist(root, chinese, Map.ofEntries(
                Map.entry("powder_mithril", t(chinese, "Mithril Powder", "秘银粉末")),
                Map.entry("powder_gemstone", t(chinese, "Gemstone Powder", "宝石粉末")),
                Map.entry("powder_glacite", t(chinese, "Glacite Powder", "冰川粉末")),
                Map.entry("tokens", t(chinese, "HOTM Tokens", "HOTM 代币"))));
        if (!cards.isEmpty()) result.add(new StatGridBlock(t(chinese, "Mining", "挖矿"), cards));
        JsonObject crystals = object(root, "crystals");
        List<String> crystalRows = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : crystals.entrySet()) {
            if (!entry.getValue().isJsonObject() || crystalRows.size() >= 12) continue;
            String state = firstString(entry.getValue().getAsJsonObject(), "state");
            if (!state.isBlank()) crystalRows.add(humanize(entry.getKey()) + " · " + humanize(state));
        }
        if (!crystalRows.isEmpty()) result.add(new ListBlock(t(chinese, "Crystals", "水晶"), crystalRows));
        return result;
    }

    private static List<Block> crimson(JsonObject root, boolean chinese) {
        List<Block> result = new ArrayList<>();
        List<StatCard> cards = new ArrayList<>();
        String faction = firstString(root, "selected_faction");
        if (!faction.isBlank()) cards.add(new StatCard(t(chinese, "Faction", "阵营"), humanize(faction), StatTone.ACCENT));
        addNumber(cards, t(chinese, "Mage Reputation", "法师声望"), firstNumber(root, "mages_reputation"));
        addNumber(cards, t(chinese, "Barbarian Reputation", "野蛮人声望"), firstNumber(root, "barbarians_reputation"));
        if (!cards.isEmpty()) result.add(new StatGridBlock(t(chinese, "Crimson Isle", "绯红岛"), cards));
        JsonObject kuudra = object(root, "kuudra_completed_tiers");
        List<StatCard> tiers = topNumberCards(kuudra, 8);
        if (!tiers.isEmpty()) result.add(new StatGridBlock(t(chinese, "Kuudra", "Kuudra"), tiers));
        return result;
    }

    private static List<Block> rift(JsonObject root, boolean chinese) {
        JsonObject access = object(root, "access");
        if (!access.has("consumed_prism")) return List.of();
        return List.of(new StatGridBlock(t(chinese, "Rift Access", "裂隙访问"), List.of(
                new StatCard(t(chinese, "Prism Consumed", "已消耗棱镜"),
                        yesNo(bool(access, "consumed_prism"), chinese), StatTone.ACCENT))));
    }

    private static List<Block> misc(JsonObject root, boolean chinese) {
        List<StatCard> cards = new ArrayList<>();
        JsonObject soul = object(root, "fairySoul", "fairy_soul");
        addNumber(cards, t(chinese, "Fairy Souls", "仙女之魂"), firstNumber(soul, "total_collected"));
        addNumber(cards, t(chinese, "Unspent Souls", "未使用之魂"), firstNumber(soul, "unspent_souls"));
        JsonObject item = object(root, "itemData", "item_data");
        addNumber(cards, t(chinese, "Soulflow", "灵魂流"), firstNumber(item, "soulflow"));
        JsonObject farming = object(root, "farming");
        JsonObject gardenData = object(farming, "garden_player_data");
        addNumber(cards, t(chinese, "Copper", "铜币"), firstNumber(gardenData, "copper"));
        return cards.isEmpty() ? List.of() : List.of(new StatGridBlock(
                t(chinese, "Other Progress", "其他进度"), cards));
    }

    private static List<Block> museum(JsonObject root, boolean chinese) {
        List<Block> result = new ArrayList<>();
        JsonObject member = object(root, "member");
        if (member.isEmpty()) member = root;
        List<StatCard> cards = new ArrayList<>();
        addCoin(cards, t(chinese, "Museum Value", "博物馆价值"), firstNumber(member, "value"));
        addCoin(cards, t(chinese, "Appraisal", "估值"), firstNumber(member, "appraisal"));
        if (!cards.isEmpty()) result.add(new StatGridBlock("", cards));
        JsonObject items = object(member, "items");
        int group = 0;
        for (Map.Entry<String, JsonElement> entry : items.entrySet()) {
            if (group >= 8 || !entry.getValue().isJsonObject()) continue;
            JsonObject donation = entry.getValue().getAsJsonObject();
            JsonObject container = firstObject(donation, "items", "item_data");
            addItemGrid(result, humanize(entry.getKey()), container, 9, 9, chinese);
            group++;
        }
        return result;
    }

    private static List<Block> garden(JsonObject root, boolean chinese) {
        JsonObject garden = object(root, "garden");
        if (garden.isEmpty()) garden = root;
        List<Block> result = new ArrayList<>();
        List<StatCard> cards = new ArrayList<>();
        addNumber(cards, t(chinese, "Garden XP", "花园经验"), firstNumber(garden, "garden_experience"));
        JsonObject commissions = object(garden, "commission_data");
        addNumber(cards, t(chinese, "Visitors Served", "已接待访客"),
                firstNumber(commissions, "total_completed", "unique_npcs_served"));
        JsonObject composter = object(garden, "composter_data");
        addNumber(cards, t(chinese, "Organic Matter", "有机物"), firstNumber(composter, "organic_matter"));
        addNumber(cards, t(chinese, "Compost", "堆肥"), firstNumber(composter, "compost_units"));
        if (!cards.isEmpty()) result.add(new StatGridBlock(t(chinese, "Garden", "花园"), cards));
        JsonObject resources = object(garden, "resources_collected");
        List<StatCard> crops = topNumberCards(resources, 18);
        if (!crops.isEmpty()) result.add(new StatGridBlock(t(chinese, "Crops", "作物"), crops));
        return result;
    }

    private static List<Block> market(JsonObject root, boolean chinese) {
        List<Block> result = new ArrayList<>();
        List<StatCard> cards = new ArrayList<>();
        boolean complete = bool(root, "estimateComplete");
        Double estimate = firstNumber(root, "knownEstimatedValue", "estimatedNetWorth");
        if (estimate != null) cards.add(new StatCard(t(chinese,
                complete ? "Net Worth" : "Known Value",
                complete ? "净资产" : "已知估值"), coins(estimate),
                complete ? StatTone.SUCCESS : StatTone.WARNING));
        addCoin(cards, t(chinese, "Item Value", "物品价值"), firstNumber(root, "itemEstimatedValue"));
        addCoin(cards, t(chinese, "Instant Sell", "即时出售"), firstNumber(root, "instantSellNetWorth"));
        addNumber(cards, t(chinese, "Priced Items", "已估价物品"), firstNumber(root, "pricedItems"));
        addNumber(cards, t(chinese, "Unknown Items", "未知价格物品"), firstNumber(root, "unknownItems"));
        if (!cards.isEmpty()) result.add(new StatGridBlock(t(chinese, "Market Summary", "市场摘要"), cards));
        JsonArray perItem = array(root, "perItem");
        List<ItemView> items = new ArrayList<>();
        int slot = 0;
        for (JsonElement element : perItem) {
            if (!element.isJsonObject() || items.size() >= MAX_ITEMS_PER_GRID) continue;
            JsonObject item = element.getAsJsonObject();
            String itemId = firstString(item, "itemId");
            if (itemId.isBlank()) continue;
            int count = Math.max(1, integer(item, "count", 1));
            items.add(new ItemView(slot++, count, itemId, firstString(item, "variantKey"),
                    humanize(itemId), "", firstNumber(item, "unitPrice"),
                    firstString(item, "confidence")));
        }
        if (!items.isEmpty()) result.add(new ItemGridBlock(
                t(chinese, "Valued Holdings", "估值物品"), List.copyOf(items), 9, items.size()));
        return result;
    }

    private static void addContainer(List<Block> blocks, JsonObject root, String title,
                                     int columns, int minimumSlots, boolean chinese, String... keys) {
        JsonObject container = firstObject(root, keys);
        addItemGrid(blocks, title, container, columns, minimumSlots, chinese);
    }

    private static void addContainer(List<Block> blocks, JsonObject root, String title,
                                     int columns, int minimumSlots, String key) {
        addContainer(blocks, root, title, columns, minimumSlots, false, key);
    }

    private static void addArmorContainer(List<Block> blocks, JsonObject root, String title,
                                          boolean chinese, String... keys) {
        int previousSize = blocks.size();
        addContainer(blocks, root, title, 4, 4, chinese, keys);
        if (blocks.size() <= previousSize || !(blocks.getLast() instanceof ItemGridBlock grid)) return;
        List<ItemView> ordered = grid.items().stream()
                .map(item -> new ItemView(item.slot() >= 0 && item.slot() < 4 ? 3 - item.slot() : item.slot(),
                        item.count(), item.itemId(), item.variantKey(), item.displayName(), item.rarity(),
                        item.unitPrice(), item.confidence()))
                .toList();
        blocks.set(blocks.size() - 1,
                new ItemGridBlock(grid.title(), ordered, grid.columns(), grid.minimumSlots()));
    }

    private static void addItemGrid(List<Block> blocks, String title, JsonObject container,
                                    int columns, int minimumSlots, boolean chinese) {
        if (container == null || container.isEmpty()) return;
        boolean limited = containsProjectionLimit(container);
        String decodeStatus = firstString(container, "decodeStatus", "decode_status");
        if (!decodeStatus.isBlank() && !"decoded".equalsIgnoreCase(decodeStatus)) {
            blocks.add(new NoticeBlock(title, t(chinese,
                    "The item data for this container is unavailable.",
                    "此容器的物品数据不可用。")));
            return;
        }
        JsonArray rawItems = array(container, "items");
        List<ItemView> items = new ArrayList<>();
        for (JsonElement element : rawItems) {
            if (!element.isJsonObject() || items.size() >= MAX_ITEMS_PER_GRID) continue;
            JsonObject item = element.getAsJsonObject();
            String itemId = firstString(item, "itemId", "item_id");
            String displayName = firstString(item, "displayName", "display_name");
            if (itemId.isBlank() && displayName.isBlank()) continue;
            int slot = Math.max(0, integer(item, "slot", items.size()));
            int count = Math.max(1, integer(item, "count", 1));
            items.add(new ItemView(slot, count, itemId, firstString(item, "variantKey", "variant_key"),
                    displayName.isBlank() ? humanize(itemId) : displayName,
                    firstString(item, "rarity").toUpperCase(Locale.ROOT), null, ""));
        }
        if (!items.isEmpty() || "decoded".equalsIgnoreCase(decodeStatus)) {
            blocks.add(new ItemGridBlock(title, List.copyOf(items), columns,
                    Math.min(MAX_ITEMS_PER_GRID, Math.max(minimumSlots, maxSlot(items) + 1))));
        }
        if (limited) {
            blocks.add(new NoticeBlock(title, t(chinese,
                    "Some slots in this container were omitted.",
                    "此容器中的部分槽位已被省略。")));
        }
    }

    private static int maxSlot(List<ItemView> items) {
        return items.stream().mapToInt(ItemView::slot).max().orElse(-1);
    }

    private static List<StatCard> statWhitelist(JsonObject object, boolean chinese,
                                                Map<String, String> labels) {
        List<StatCard> cards = new ArrayList<>();
        for (Map.Entry<String, String> label : labels.entrySet()) {
            Double value = firstNumber(object, label.getKey());
            if (value == null || value == 0.0) continue;
            cards.add(new StatCard(label.getValue(), formatNumber(value), StatTone.NORMAL));
        }
        return List.copyOf(cards);
    }

    private static List<StatCard> topNumberCards(JsonObject object, int limit) {
        return object.entrySet().stream()
                .map(entry -> new NumericEntry(entry.getKey(), number(entry.getValue())))
                .filter(entry -> entry.value() != null && entry.value() != 0.0)
                .sorted((left, right) -> Double.compare(Math.abs(right.getValue()), Math.abs(left.getValue())))
                .limit(limit)
                .map(entry -> new StatCard(humanize(entry.key()), formatNumber(entry.value()), StatTone.NORMAL))
                .toList();
    }

    private static SkillProgress skillProgress(double totalXp) {
        double remaining = Math.max(0.0, totalXp);
        int level = 0;
        for (double requirement : SKILL_XP) {
            if (remaining < requirement) {
                return new SkillProgress(level, requirement <= 0.0 ? 0.0 : remaining / requirement);
            }
            remaining -= requirement;
            level++;
        }
        return new SkillProgress(SKILL_XP.length, 1.0);
    }

    private static SectionView empty(String title, String icon, String message) {
        return new SectionView(title, icon, List.of(new EmptyBlock(message)));
    }

    private static ProfileDescriptor selected(ProfileSnapshot snapshot) {
        if (snapshot == null || snapshot.profiles().isEmpty()) return null;
        return snapshot.profiles().stream()
                .filter(profile -> profile.profileId().equals(snapshot.selectedProfileId()))
                .findFirst().orElse(snapshot.profiles().getFirst());
    }

    private static JsonObject payload(ProfileSnapshot snapshot, ProfileSectionId sectionId) {
        return snapshot == null ? new JsonObject()
                : snapshot.section(sectionId).map(ProfileSection::payload).orElseGet(JsonObject::new);
    }

    private static JsonObject object(JsonObject root, String... keys) {
        if (root == null) return new JsonObject();
        for (String key : keys) {
            JsonElement element = root.get(key);
            if (element != null && element.isJsonObject()) return element.getAsJsonObject();
        }
        return new JsonObject();
    }

    private static JsonObject firstObject(JsonObject root, String... keys) {
        return object(root, keys);
    }

    private static JsonArray array(JsonObject root, String... keys) {
        if (root == null) return new JsonArray();
        for (String key : keys) {
            JsonElement element = root.get(key);
            if (element != null && element.isJsonArray()) return element.getAsJsonArray();
        }
        return new JsonArray();
    }

    static boolean rootProjectionLimited(JsonElement element) {
        if (element == null || element.isJsonNull()) return false;
        if (projectionMarker(element)) return true;
        if (!element.isJsonObject()) return false;
        JsonObject object = element.getAsJsonObject();
        JsonElement value = object.get("value");
        return value != null && projectionMarker(value);
    }

    static boolean containsProjectionLimit(JsonElement element) {
        if (element == null || element.isJsonNull()) return false;
        if (projectionMarker(element)) return true;
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                if (containsProjectionLimit(child)) return true;
            }
            return false;
        }
        if (!element.isJsonObject()) return false;
        JsonObject object = element.getAsJsonObject();
        if (object.has("_truncated")) return true;
        String status = firstString(object, "status");
        if ("truncated".equalsIgnoreCase(status) || "omitted".equalsIgnoreCase(status)) return true;
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (containsProjectionLimit(entry.getValue())) return true;
        }
        return false;
    }

    private static boolean projectionMarker(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isString()) return false;
        String value = element.getAsString();
        return "<node-limit>".equals(value) || "<depth-limit>".equals(value);
    }

    private static String firstString(JsonObject object, String... keys) {
        if (object == null) return "";
        for (String key : keys) {
            JsonElement element = object.get(key);
            if (element != null && element.isJsonPrimitive()
                    && element.getAsJsonPrimitive().isString()) {
                String value = element.getAsString().trim();
                if (!value.isBlank() && !projectionMarker(element)) return value;
            }
        }
        return "";
    }

    private static Double firstNumber(JsonObject object, String... keys) {
        if (object == null) return null;
        for (String key : keys) {
            Double value = number(object.get(key));
            if (value != null) return value;
        }
        return null;
    }

    private static double numberOrZero(JsonObject object, String key) {
        Double value = firstNumber(object, key);
        return value == null ? 0.0 : value;
    }

    private static Double number(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isNumber()) return null;
        try {
            double value = element.getAsDouble();
            return Double.isFinite(value) ? value : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static boolean bool(JsonObject object, String key) {
        JsonElement element = object == null ? null : object.get(key);
        return element != null && element.isJsonPrimitive()
                && element.getAsJsonPrimitive().isBoolean() && element.getAsBoolean();
    }

    private static int integer(JsonObject object, String key, int fallback) {
        Double value = firstNumber(object, key);
        if (value == null || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) return fallback;
        return value.intValue();
    }

    private static void addCoin(List<StatCard> cards, String label, Double value) {
        if (value != null) cards.add(new StatCard(label, coins(value), StatTone.SUCCESS));
    }

    private static void addNumber(List<StatCard> cards, String label, Double value) {
        if (value != null) cards.add(new StatCard(label, formatNumber(value), StatTone.NORMAL));
    }

    static String coins(double value) {
        return formatCompact(value) + " coins";
    }

    static String formatNumber(double value) {
        if (Math.abs(value) >= 10_000.0) return formatCompact(value);
        if (Math.rint(value) == value) return Long.toString((long) value);
        return decimal(value, 2);
    }

    private static String formatCompact(double value) {
        double absolute = Math.abs(value);
        String suffix = "";
        double scaled = value;
        if (absolute >= 1_000_000_000_000.0) {
            suffix = "T";
            scaled /= 1_000_000_000_000.0;
        } else if (absolute >= 1_000_000_000.0) {
            suffix = "B";
            scaled /= 1_000_000_000.0;
        } else if (absolute >= 1_000_000.0) {
            suffix = "M";
            scaled /= 1_000_000.0;
        } else if (absolute >= 1_000.0) {
            suffix = "K";
            scaled /= 1_000.0;
        }
        return decimal(scaled, absolute >= 1_000.0 ? 2 : 0) + suffix;
    }

    private static String decimal(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }

    static String humanize(String value) {
        if (value == null || value.isBlank()) return "—";
        String text = value.trim().replace('-', ' ').replace('_', ' ')
                .replaceAll("([a-z0-9])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder(text.length());
        boolean upper = true;
        for (char character : text.toCharArray()) {
            if (upper && Character.isLetter(character)) {
                result.append(Character.toUpperCase(character));
                upper = false;
            } else {
                result.append(character);
                upper = character == ' ';
            }
        }
        return result.toString();
    }

    static String displayRank(String raw) {
        if (raw == null || raw.isBlank() || "NONE".equalsIgnoreCase(raw)) return "";
        return switch (raw.toUpperCase(Locale.ROOT)) {
            case "MVP_PLUS" -> "[MVP+]";
            case "MVP_PLUS_PLUS" -> "[MVP++]";
            case "MVP" -> "[MVP]";
            case "VIP_PLUS" -> "[VIP+]";
            case "VIP" -> "[VIP]";
            case "YOUTUBER" -> "[YOUTUBE]";
            case "ADMIN" -> "[ADMIN]";
            default -> "[" + raw.replace('_', ' ') + "]";
        };
    }

    private static String yesNo(boolean value, boolean chinese) {
        return value ? t(chinese, "Yes", "是") : t(chinese, "No", "否");
    }

    static String sectionTitle(ProfileSectionId section, boolean chinese) {
        return switch (section) {
            case OVERVIEW -> t(chinese, "Overview", "概览");
            case GEAR -> t(chinese, "Equipment", "装备");
            case ACCESSORIES -> t(chinese, "Accessories", "饰品");
            case PETS -> t(chinese, "Pets", "宠物");
            case INVENTORY -> t(chinese, "Inventory", "背包");
            case SKILLS -> t(chinese, "Skills", "技能");
            case SLAYER -> "Slayer";
            case MINIONS -> "Minions";
            case BESTIARY -> t(chinese, "Bestiary", "生物图鉴");
            case COLLECTIONS -> t(chinese, "Collections", "收藏");
            case MINING -> t(chinese, "Mining", "挖矿");
            case CRIMSON_ISLE -> t(chinese, "Crimson Isle", "绯红岛");
            case RIFT -> t(chinese, "The Rift", "裂隙");
            case MISC -> t(chinese, "Farming & Other", "农业与其他");
            case MUSEUM -> t(chinese, "Museum", "博物馆");
            case GARDEN -> t(chinese, "Garden", "花园");
            case MARKET -> t(chinese, "Market", "市场");
        };
    }

    static String sectionIcon(ProfileSectionId section) {
        return switch (section) {
            case OVERVIEW -> "O";
            case GEAR -> "G";
            case ACCESSORIES -> "A";
            case PETS -> "P";
            case INVENTORY -> "I";
            case SKILLS -> "S";
            case SLAYER -> "X";
            case MINIONS -> "M";
            case BESTIARY -> "B";
            case COLLECTIONS -> "C";
            case MINING -> "D";
            case CRIMSON_ISLE -> "K";
            case RIFT -> "R";
            case MISC -> "F";
            case MUSEUM -> "U";
            case GARDEN -> "N";
            case MARKET -> "$";
        };
    }

    private static String t(boolean chinese, String english, String zh) {
        return chinese ? zh : english;
    }

    record HeaderView(String name, String rank, String profileName, String mode, int memberCount) {
    }

    record SectionView(String title, String icon, List<Block> blocks) {
    }

    sealed interface Block permits StatGridBlock, ProgressBlock, ItemGridBlock,
            PetGridBlock, ListBlock, NoticeBlock, EmptyBlock {
    }

    record StatGridBlock(String title, List<StatCard> cards) implements Block {
    }

    record StatCard(String label, String value, StatTone tone) {
    }

    enum StatTone { NORMAL, ACCENT, SUCCESS, WARNING }

    record ProgressBlock(String title, List<ProgressEntry> entries) implements Block {
    }

    record ProgressEntry(String label, String level, String detail, double progress) {
        ProgressEntry {
            progress = Math.clamp(progress, 0.0, 1.0);
        }
    }

    record ItemGridBlock(String title, List<ItemView> items, int columns, int minimumSlots) implements Block {
    }

    record ItemView(int slot, int count, String itemId, String variantKey,
                    String displayName, String rarity, Double unitPrice, String confidence) {
        ItemView {
            slot = Math.max(0, slot);
            count = Math.max(1, count);
            itemId = itemId == null ? "" : itemId;
            variantKey = variantKey == null ? "" : variantKey;
            displayName = displayName == null || displayName.isBlank() ? humanize(itemId) : displayName;
            rarity = rarity == null ? "" : rarity;
            confidence = confidence == null ? "" : confidence;
        }
    }

    record PetGridBlock(String title, List<PetView> pets) implements Block {
    }

    record PetView(String name, String rarity, double experience, boolean active, String heldItem) {
    }

    record ListBlock(String title, List<String> rows) implements Block {
    }

    record NoticeBlock(String title, String message) implements Block {
    }

    record EmptyBlock(String message) implements Block {
    }

    private record SkillProgress(int level, double progress) {
    }

    private record NumericEntry(String key, Double value) {
        double getValue() {
            return value == null ? 0.0 : value;
        }
    }
}
