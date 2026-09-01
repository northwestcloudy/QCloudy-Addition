package cloudy.autume.addition.profile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProfileCommandsTest {
    @Test
    void rootsMapToTheDocumentedSlashForms() {
        assertEquals("/pv", ProfileCommands.DOUBLE_SLASH_ROOT);
        assertEquals("qpv", ProfileCommands.SINGLE_SLASH_ROOT);
    }

    @Test
    void missingTargetUsesTheLocalPlayer() {
        assertEquals("NorthwestCloudy", ProfileCommands.normalizeTarget(null, "NorthwestCloudy"));
        assertEquals("NorthwestCloudy", ProfileCommands.normalizeTarget("   ", " NorthwestCloudy "));
    }

    @Test
    void explicitTargetIsTrimmedAndPreserved() {
        assertEquals("_Qcloudy2233_",
                ProfileCommands.normalizeTarget("  _Qcloudy2233_  ", "someoneElse"));
    }

    @Test
    void supportedTargetsCoverNamesAndBothUuidForms() {
        assertTrue(ProfileCommands.isSupportedTarget("northwestcloudy"));
        assertTrue(ProfileCommands.isSupportedTarget("f84c6a790a4e45e0a421d10f5f3bc49a"));
        assertTrue(ProfileCommands.isSupportedTarget("f84c6a79-0a4e-45e0-a421-d10f5f3bc49a"));
    }

    @Test
    void unsupportedTargetsAreRejectedByTheSharedShapeCheck() {
        assertFalse(ProfileCommands.isSupportedTarget(null));
        assertFalse(ProfileCommands.isSupportedTarget(""));
        assertFalse(ProfileCommands.isSupportedTarget("ab"));
        assertFalse(ProfileCommands.isSupportedTarget("player with spaces"));
        assertFalse(ProfileCommands.isSupportedTarget("12345678901234567"));
        assertFalse(ProfileCommands.isSupportedTarget("not-a-valid-uuid"));
    }
}
