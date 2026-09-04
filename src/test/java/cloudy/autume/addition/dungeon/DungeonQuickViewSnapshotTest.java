package cloudy.autume.addition.dungeon;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DungeonQuickViewSnapshotTest {
    static final String JSON = """
            {
              "schemaVersion":1,
              "identity":{"uuid":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","name":"GhostsTM"},
              "catacombs":{"level":40.2,"xp":51359640},
              "classes":{
                "healer":{"level":30.1,"xp":3084640},
                "mage":{"level":41.0,"xp":66359640},
                "berserk":{"level":39.5,"xp":45000000},
                "archer":{"level":42.0,"xp":85359640},
                "tank":{"level":31.0,"xp":4149640}
              },
              "floor":{"id":"M7","runs":312,"fastestMs":298321},
              "secrets":{"total":2432,"averagePerRun":11.4},
              "magicalPower":1330,
              "armor":[
                {"itemId":"GOLDEN_NECRON_HEAD","name":"§6Ancient Golden Necron Head","lore":["§7Health: +100"],"rarity":"LEGENDARY"},
                null,null,null
              ],
              "weapons":{
                "witherBlade":{"present":true,"item":{"itemId":"HYPERION","name":"§dHyperion","lore":[],"rarity":"MYTHIC"}},
                "terminator":{"present":false,"item":null},"complete":true
              },
              "pets":{
                "goldenDragon":{"present":true,"item":{"itemId":"PET","name":"§6Golden Dragon","lore":["§7XP: §b1,000"],"rarity":"LEGENDARY"}},
                "enderDragon":{"present":false,"item":null},"complete":true
              },
              "metadata":{"status":"fresh","fetchedAt":1000}
            }
            """;

    @Test
    void parsesTheDedicatedBoundedContractAndTriState() {
        DungeonQuickViewSnapshot view = DungeonQuickViewSnapshot.parse(JSON);
        assertEquals("GhostsTM", view.playerName());
        assertEquals(40.2, view.catacombs().level());
        assertEquals("M7", view.floor().id());
        assertEquals(312, view.floor().runs());
        assertEquals(DungeonQuickViewSnapshot.PresenceState.PRESENT, view.witherBlade().state());
        assertEquals(DungeonQuickViewSnapshot.PresenceState.ABSENT, view.terminator().state());
        assertEquals("§6Ancient Golden Necron Head", view.armor().getFirst().name());
    }

    @Test
    void preservesNativeUnderlinesAndManualKickClick() {
        DungeonQuickViewSnapshot view = DungeonQuickViewSnapshot.parse(JSON);
        Component message = DungeonQuickViewMessage.build(view, String::length,
                (item, kind) -> new HoverEvent.ShowText(Component.literal(item.name())));
        List<Component> parts = flatten(message);
        Component healer = parts.stream().filter(part -> part.getString().startsWith("Heal. "))
                .findFirst().orElseThrow();
        Component kick = parts.stream().filter(part -> part.getString().equals(
                "CLICK HERE TO KICK THE PLAYER OUT")).findFirst().orElseThrow();
        assertTrue(healer.getStyle().isUnderlined());
        assertTrue(kick.getStyle().isUnderlined());
        assertTrue(kick.getStyle().isBold());
        ClickEvent.RunCommand click = assertInstanceOf(ClickEvent.RunCommand.class,
                kick.getStyle().getClickEvent());
        assertEquals("/party kick GhostsTM", click.command());
        assertNotNull(parts.stream().filter(part -> part.getString().equals("Withered Blade ✔"))
                .findFirst().orElseThrow().getStyle().getHoverEvent());
    }

    @Test
    void separatorEndpointsUseMeasuredWidth() {
        DungeonQuickViewMessage.Lines lines = DungeonQuickViewMessage.separators(String::length);
        assertTrue(Math.abs(lines.topWidth() - lines.bottomWidth()) <= 1);
    }

    @Test
    void separatorEndpointsIncludeTheBoldTitleWidth() {
        DungeonQuickViewMessage.Lines lines = DungeonQuickViewMessage.separators(
                String::length, 40);
        assertTrue(Math.abs(lines.topWidth() - lines.bottomWidth()) <= 1);
    }

    @Test
    void rejectsAResponseNameThatCouldChangeTheKickCommand() {
        String unsafe = JSON.replace("\"name\":\"GhostsTM\"", "\"name\":\"GhostsTM kick Other\"");
        assertThrows(DungeonQuickViewException.class, () -> DungeonQuickViewSnapshot.parse(unsafe));
    }

    private static List<Component> flatten(Component root) {
        List<Component> result = new ArrayList<>();
        walk(root, result);
        return result;
    }

    private static void walk(Component component, List<Component> target) {
        target.add(component);
        for (Component sibling : component.getSiblings()) walk(sibling, target);
    }

}
