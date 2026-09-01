package cloudy.autume.addition.profile.ui;

import com.google.gson.JsonObject;

import java.util.List;

/** Developer-only escape hatch. Raw payloads are never visible in production. */
final class ProfileDebugFallback {
    private static final boolean ENABLED = false;

    private ProfileDebugFallback() {
    }

    static boolean enabled() {
        return ENABLED;
    }

    static List<ProfilePresentationMapper.Block> blocks(JsonObject payload, boolean chinese) {
        if (!ENABLED) return List.of();
        return List.of(new ProfilePresentationMapper.NoticeBlock(
                chinese ? "开发者数据" : "Developer data",
                chinese ? "原始调试视图仅在开发构建中可用。" :
                        "The raw diagnostic view is available only in development builds."));
    }
}
