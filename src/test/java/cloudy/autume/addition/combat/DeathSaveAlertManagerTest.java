package cloudy.autume.addition.combat;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class DeathSaveAlertManagerTest {
    @Test
    void strictlyMatchesTheConfirmedServerMessagesAndReturnsRequestedTitles() {
        Map<String, Expected> cases = Map.of(
                "Second Wind Activated! Your Spirit Mask saved your life!",
                new Expected(DeathSaveAlertManager.Ability.SPIRIT_MASK,
                        "!You've Been Saved By Spirit Mask!", 30),
                "Your Bonzo's Mask saved your life!",
                new Expected(DeathSaveAlertManager.Ability.BONZO_MASK,
                        "!You've Been Saved By Bonzo's Mask!", 360),
                "Your \uE068 Bonzo's Mask saved your life!",
                new Expected(DeathSaveAlertManager.Ability.BONZO_MASK,
                        "!You've Been Saved By Bonzo's Mask!", 360),
                "Your Phoenix Pet saved you from certain death!",
                new Expected(DeathSaveAlertManager.Ability.PHOENIX,
                        "!You've Been Saved By Phoenix!", 60));

        cases.forEach((message, expected) -> {
            DeathSaveAlertManager manager = new DeathSaveAlertManager();
            long now = 1_000L;
            DeathSaveAlertManager.Alert alert = manager.onMessage(message, now);

            assertEquals(expected.ability(), alert.ability());
            assertEquals(expected.title(), alert.centerTitle());
            assertEquals(expected.cooldownSeconds(), alert.ability().baseCooldownSeconds());
            assertEquals(now + Duration.ofSeconds(expected.cooldownSeconds()).toNanos(),
                    alert.readyAtNanos());
        });
    }

    @Test
    void treatsUpgradedAndOrdinaryBonzoMessagesAsAliasesOfTheSameAbility() {
        DeathSaveAlertManager manager = new DeathSaveAlertManager();
        long now = 2_000L;

        DeathSaveAlertManager.Alert upgraded = manager.onMessage(
                "Your \uE068 Bonzo's Mask saved your life!", now);

        assertEquals(DeathSaveAlertManager.Ability.BONZO_MASK, upgraded.ability());
        assertEquals(now + DeathSaveAlertManager.Ability.BONZO_MASK.baseCooldownNanos(),
                upgraded.readyAtNanos());
        assertNull(manager.onMessage("Your Bonzo's Mask saved your life!",
                now + DeathSaveAlertManager.DUPLICATE_WINDOW_NANOS - 1));
        assertEquals(upgraded.readyAtNanos(),
                manager.readyAtNanos(DeathSaveAlertManager.Ability.BONZO_MASK));

        long later = now + DeathSaveAlertManager.DUPLICATE_WINDOW_NANOS;
        DeathSaveAlertManager.Alert ordinary = manager.onMessage(
                "Your Bonzo's Mask saved your life!", later);
        assertEquals(later + DeathSaveAlertManager.Ability.BONZO_MASK.baseCooldownNanos(),
                ordinary.readyAtNanos());
        assertEquals(ordinary.readyAtNanos(),
                manager.readyAtNanos(DeathSaveAlertManager.Ability.BONZO_MASK));
    }

    @Test
    void removesFormattingAndWhitespaceWithoutAcceptingNearMatches() {
        DeathSaveAlertManager manager = new DeathSaveAlertManager();

        assertEquals(DeathSaveAlertManager.Ability.PHOENIX,
                manager.onMessage("  §6Your Phoenix Pet saved you from certain death!§r  ", 0L).ability());
        assertEquals(DeathSaveAlertManager.Ability.BONZO_MASK,
                manager.onMessage("  §aYour §b\uE068 §9Bonzo's Mask§a saved your life!§r  ", 5L)
                        .ability());
        assertNull(manager.onMessage("Your Phoenix Pet saved you from certain death.", 10L));
        assertNull(manager.onMessage("Your Phoenix Pet saved someone from certain death!", 20L));
        assertNull(manager.onMessage("Your Bonzo’s Mask saved your life!", 30L));
        assertNull(manager.onMessage("Your \uE067 Bonzo's Mask saved your life!", 35L));
        assertNull(manager.onMessage("Your Bonzo's Mask saved your life! extra", 40L));
        assertNull(manager.onMessage("Your Spirit Mask saved your life!", 50L));
        assertNull(manager.onMessage(null, 60L));
    }

    @Test
    void deduplicatesEachAbilityIndependentlyWithoutMovingItsDeadline() {
        DeathSaveAlertManager manager = new DeathSaveAlertManager();
        long now = 5_000L;

        DeathSaveAlertManager.Alert spirit = manager.onMessage(
                "Second Wind Activated! Your Spirit Mask saved your life!", now);
        DeathSaveAlertManager.Alert phoenix = manager.onMessage(
                "Your Phoenix Pet saved you from certain death!", now);
        long spiritReadyAt = spirit.readyAtNanos();

        assertEquals(DeathSaveAlertManager.Ability.PHOENIX, phoenix.ability());
        assertNull(manager.onMessage(
                "Second Wind Activated! Your Spirit Mask saved your life!",
                now + DeathSaveAlertManager.DUPLICATE_WINDOW_NANOS - 1));
        assertEquals(spiritReadyAt,
                manager.readyAtNanos(DeathSaveAlertManager.Ability.SPIRIT_MASK));
    }

    @Test
    void aLaterRealActivationResetsOnlyThatAbilityToItsFullCooldown() {
        DeathSaveAlertManager manager = new DeathSaveAlertManager();
        long first = 10_000L;
        long second = first + DeathSaveAlertManager.DUPLICATE_WINDOW_NANOS;

        manager.onMessage("Your Bonzo's Mask saved your life!", first);
        DeathSaveAlertManager.Alert phoenix = manager.onMessage(
                "Your Phoenix Pet saved you from certain death!", first);
        DeathSaveAlertManager.Alert bonzoAgain = manager.onMessage(
                "Your Bonzo's Mask saved your life!", second);

        assertEquals(second + DeathSaveAlertManager.Ability.BONZO_MASK.baseCooldownNanos(),
                bonzoAgain.readyAtNanos());
        assertEquals(phoenix.readyAtNanos(),
                manager.readyAtNanos(DeathSaveAlertManager.Ability.PHOENIX));
        assertEquals(DeathSaveAlertManager.Ability.BONZO_MASK.baseCooldownNanos(),
                manager.remainingNanos(DeathSaveAlertManager.Ability.BONZO_MASK, second));
    }

    @Test
    void remainingTimeStopsAtZeroAndResetSilentlyClearsTimersAndDedupe() {
        DeathSaveAlertManager manager = new DeathSaveAlertManager();
        long now = 20_000L;
        DeathSaveAlertManager.Alert first = manager.onMessage(
                "Your Phoenix Pet saved you from certain death!", now);

        assertEquals(1L,
                manager.remainingNanos(DeathSaveAlertManager.Ability.PHOENIX,
                        first.readyAtNanos() - 1));
        assertEquals(0L,
                manager.remainingNanos(DeathSaveAlertManager.Ability.PHOENIX,
                        first.readyAtNanos()));

        manager.reset();
        assertEquals(0L, manager.readyAtNanos(DeathSaveAlertManager.Ability.PHOENIX));
        assertEquals(0L, manager.remainingNanos(DeathSaveAlertManager.Ability.PHOENIX, now));
        assertEquals(DeathSaveAlertManager.Ability.PHOENIX,
                manager.onMessage("Your Phoenix Pet saved you from certain death!", now).ability());
    }

    @Test
    void sharedRuntimeCanBeReadByHudAndResetOnDisconnect() {
        DeathSaveAlertManager.resetRuntime();
        try {
            DeathSaveAlertManager.Alert alert = DeathSaveAlertManager.handle(
                    "Your Phoenix Pet saved you from certain death!");

            assertEquals(DeathSaveAlertManager.Ability.PHOENIX, alert.ability());
            long remaining = DeathSaveAlertManager.remainingMillis(
                    DeathSaveAlertManager.Ability.PHOENIX);
            assertEquals(true, remaining > 0L && remaining <= 60_000L);

            DeathSaveAlertManager.resetRuntime();
            assertEquals(0L, DeathSaveAlertManager.remainingMillis(
                    DeathSaveAlertManager.Ability.PHOENIX));
        } finally {
            DeathSaveAlertManager.resetRuntime();
        }
    }

    private record Expected(DeathSaveAlertManager.Ability ability, String title,
                            long cooldownSeconds) {
    }
}
