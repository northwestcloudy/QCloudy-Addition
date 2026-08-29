package cloudy.autume.addition.inventory;

import cloudy.autume.addition.config.ConfigManager;
import cloudy.autume.addition.config.ModConfig;
import cloudy.autume.addition.i18n.ModText;
import cloudy.autume.addition.tracker.LocationTracker;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoField;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

public final class ItemTimestampTooltip {
    private static final ZoneId HYPIXEL_ZONE = ZoneId.of("America/New_York");
    private static final Pattern DURATION = Pattern.compile(
            "(?i)(?:(?<years>[0-9]+) ?(?:y|years?) )?(?:(?<days>[0-9]+) ?(?:d|days?))? ?"
                    + "(?:(?<hours>[0-9]+) ?(?:h|hours?))? ?(?:(?<minutes>[0-9]+) ?(?:m|minutes?))? ?"
                    + "(?:(?<seconds>[0-9]+) ?(?:s|seconds?))?\\b");
    private static final DateTimeFormatter LOCAL_24H = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd HH:mm:ss z").toFormatter();
    private static final DateTimeFormatter LOCAL_12H = new DateTimeFormatterBuilder()
            .appendPattern("MMM d, yyyy h:mm:ss a z").toFormatter(Locale.US);

    private ItemTimestampTooltip() {
    }

    public static void register() {
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            var config = ConfigManager.get().inventory;
            if (!enabled(config, LocationTracker.isSkyBlock())) return;
            if (config.showCreationTimestamp) {
                Instant timestamp = SkyBlockItemData.timestamp(stack);
                if (timestamp != null) {
                    lines.add(Component.literal(ModText.get("tooltip.item_created", format(timestamp)))
                            .withStyle(ChatFormatting.DARK_GRAY));
                }
            }
            if (config.showCountdownCompletion) appendCountdowns(lines);
        });
    }

    static boolean enabled(ModConfig.Inventory config, boolean inSkyBlock) {
        return config.itemTimestamps && inSkyBlock;
    }

    static void appendCountdowns(List<Component> lines) {
        ZonedDateTime lastTimer = null;
        for (int index = 0; index < lines.size(); index++) {
            String plain = lines.get(index).getString();
            CountdownType type = CountdownType.match(plain);
            if (type == null) continue;
            MatchResult lastMatch = null;
            Matcher matcher = DURATION.matcher(plain);
            while (matcher.find()) if (!matcher.group().isBlank()) lastMatch = matcher.toMatchResult();
            if (lastMatch == null) continue;
            long years = group(lastMatch, "years");
            long days = group(lastMatch, "days");
            long hours = group(lastMatch, "hours");
            long minutes = group(lastMatch, "minutes");
            long seconds = group(lastMatch, "seconds");
            if (years + days + hours + minutes + seconds == 0) continue;
            ZonedDateTime baseline = type.relative ? lastTimer : ZonedDateTime.now(HYPIXEL_ZONE);
            if (baseline == null) continue;
            ZonedDateTime completion = baseline.plusYears(years).plusDays(days).plusHours(hours)
                    .plusMinutes(minutes).plusSeconds(seconds);
            lastTimer = completion;
            lines.add(++index, Component.literal(ModText.get(type.translationKey) + ": ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(format(completion.toInstant())).withStyle(ChatFormatting.AQUA)));
        }
    }

    private static long group(MatchResult matcher, String name) {
        String value = matcher.group(name);
        return value == null ? 0 : Long.parseLong(value);
    }

    static String format(Instant instant) {
        ZonedDateTime local = instant.atZone(ZoneId.systemDefault());
        return switch (ConfigManager.get().inventory.timestampFormat) {
            case "LOCAL_12H" -> LOCAL_12H.format(local);
            case "ISO" -> DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(local);
            case "RFC" -> DateTimeFormatter.RFC_1123_DATE_TIME.format(local);
            default -> LOCAL_24H.format(local);
        };
    }

    private enum CountdownType {
        START("Starting in:", "tooltip.timer.starts", false),
        STARTS("Starts in:", "tooltip.timer.starts", false),
        INTEREST("Interest in:", "tooltip.timer.available", false),
        UNTIL_INTEREST("Until interest:", "tooltip.timer.available", false),
        ENDS("Ends in:", "tooltip.timer.ends", false),
        REMAINING("Remaining:", "tooltip.timer.ends", false),
        DURATION("Duration:", "tooltip.timer.ends", false),
        TIME_LEFT("Time left:", "tooltip.timer.ends", false),
        EVENT_TIME_LEFT("Event lasts for", "tooltip.timer.ends", true),
        AUCTION("Auction ends in:", "tooltip.timer.auction", false),
        CALENDAR(" (§e", "tooltip.timer.starts", false),
        CONTRIBUTE("Contribute again", "tooltip.timer.available", false),
        NEXT_CHARGE("Next Charge", "tooltip.timer.available", false),
        STONKS("Auction ends in", "tooltip.timer.ends", false),
        RESETS("Resets in:", "tooltip.timer.resets", false),
        COOLDOWN("Cooldown:", "tooltip.timer.available", false),
        ON_COOLDOWN("On cooldown:", "tooltip.timer.available", false),
        EVENT_ENDING("Event ends in:", "tooltip.timer.ends", false),
        NEXT_STAGE("Next Stage:", "tooltip.timer.available", false),
        UNTIL_READY("Until ready:", "tooltip.timer.available", false),
        TIME_UNTIL("Time until:", "tooltip.timer.available", false);

        final String marker;
        final String translationKey;
        final boolean relative;

        CountdownType(String marker, String translationKey, boolean relative) {
            this.marker = marker;
            this.translationKey = translationKey;
            this.relative = relative;
        }

        static CountdownType match(String line) {
            for (CountdownType value : values()) {
                if (line.toLowerCase(Locale.ROOT).contains(value.marker.toLowerCase(Locale.ROOT))) return value;
            }
            return null;
        }
    }
}
