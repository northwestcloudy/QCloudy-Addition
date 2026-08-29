package cloudy.autume.addition.update;

import cloudy.autume.addition.QCloudyAdditionClient;
import cloudy.autume.addition.compat.MinecraftClientCompat;
import cloudy.autume.addition.i18n.ModText;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Always-on, release-only update notification. It never downloads a JAR. */
public final class ReleaseUpdateChecker {
    static final URI MANIFEST_URI = URI.create(
            "https://www.qcloudy.net/assets/data/release-manifest.json");
    static final URI DOWNLOAD_URI = URI.create("https://qcloudy.net/download/");
    static final URI CHANGELOG_URI = URI.create("https://qcloudy.net/changelog/");
    static final int MAX_RESPONSE_BYTES = 128 * 1024;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final long START_DELAY_SECONDS = 5;
    private static final SystemToast.SystemToastId UPDATE_TOAST =
            new SystemToast.SystemToastId();

    private final ReleaseBuildInfo build;
    private final HttpClient httpClient;
    private final ReleaseUpdateState state = new ReleaseUpdateState();

    ReleaseUpdateChecker(ReleaseBuildInfo build, HttpClient httpClient) {
        this.build = build;
        this.httpClient = httpClient;
    }

    public static ReleaseUpdateChecker createDefault() {
        ReleaseBuildInfo info;
        try {
            info = ReleaseBuildInfo.load();
        } catch (IOException exception) {
            QCloudyAdditionClient.LOGGER.warn(
                    "Stable Release update checks are disabled: invalid embedded release metadata", exception);
            info = new ReleaseBuildInfo("Invalid", "unknown", "unknown", 1);
        }
        return new ReleaseUpdateChecker(info, defaultHttpClient());
    }

    static HttpClient defaultHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /** Called from Fabric's client JOIN event. */
    public void onJoin(Minecraft client) {
        deliverPending(client);
        // Alpha builds and invalid metadata return here without scheduling or requesting.
        if (!state.beginRequest(build)) return;
        CompletableFuture.delayedExecutor(START_DELAY_SECONDS, TimeUnit.SECONDS)
                .execute(this::requestManifest);
    }

    private void requestManifest() {
        httpClient.sendAsync(buildRequest(), HttpResponse.BodyHandlers.ofInputStream())
                .thenApply(response -> parseResponse(response.statusCode(), response.body(), build))
                .whenComplete((release, error) -> {
                    if (error != null) {
                        QCloudyAdditionClient.LOGGER.debug(
                                "Stable Release update check failed; it will be retried next launch", error);
                        return;
                    }
                    release.ifPresent(this::queueForClient);
                });
    }

    HttpRequest buildRequest() {
        return HttpRequest.newBuilder(MANIFEST_URI)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", "QCloudy_Addition/" + build.version())
                .GET()
                .build();
    }

    static Optional<ReleaseManifest.AvailableRelease> parseResponse(
            int statusCode, InputStream responseBody, ReleaseBuildInfo build) {
        try (InputStream body = responseBody) {
            if (statusCode != 200) return Optional.empty();
            byte[] bytes = body.readNBytes(MAX_RESPONSE_BYTES + 1);
            if (bytes.length > MAX_RESPONSE_BYTES) return Optional.empty();
            String json = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            return ReleaseManifest.findUpdate(json, build);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read Release manifest", exception);
        }
    }

    private void queueForClient(ReleaseManifest.AvailableRelease release) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            state.queue(release);
            deliverPending(client);
        });
    }

    private void deliverPending(Minecraft client) {
        state.takeForDisplay(client != null && client.player != null)
                .ifPresent(release -> display(client, release));
    }

    private void display(Minecraft client, ReleaseManifest.AvailableRelease release) {
        SystemToast.addOrUpdate(MinecraftClientCompat.toastManager(client), UPDATE_TOAST,
                ModText.component("update.release.toast.title"),
                ModText.component("update.release.toast.body", release.version()));

        Component message = ModText.component("update.release.chat", release.version(), build.displayVersion())
                .withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" "))
                .append(link("update.release.download", "update.release.download.hover", DOWNLOAD_URI))
                .append(Component.literal("  "))
                .append(link("update.release.changelog", "update.release.changelog.hover", CHANGELOG_URI));
        client.player.sendSystemMessage(message);
    }

    private static Component link(String labelKey, String hoverKey, URI uri) {
        return ModText.component(labelKey).withStyle(style -> style
                .withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent.OpenUrl(uri))
                .withHoverEvent(new HoverEvent.ShowText(ModText.component(hoverKey))));
    }
}
