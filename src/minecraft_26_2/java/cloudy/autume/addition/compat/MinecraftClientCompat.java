package cloudy.autume.addition.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Minecraft 26.2 client GUI access points used by shared QCA code. */
public final class MinecraftClientCompat {
    private MinecraftClientCompat() {
    }

    public static Screen screen(Minecraft client) {
        return client.gui.screen();
    }

    public static void setScreen(Minecraft client, Screen screen) {
        client.gui.setScreen(screen);
    }

    public static boolean hasOverlay(Minecraft client) {
        return client.gui.overlay() != null;
    }

    public static boolean isHudHidden(Minecraft client) {
        return client.gui.hud.isHidden();
    }

    public static ChatComponent chat(Minecraft client) {
        return client.gui.hud.getChat();
    }

    public static PlayerTabOverlay tabList(Minecraft client) {
        return client.gui.hud.getTabList();
    }

    public static ToastManager toastManager(Minecraft client) {
        return client.gui.toastManager();
    }

    public static void showTitle(Minecraft client, Component title, Component subtitle,
                                 int fadeIn, int stay, int fadeOut) {
        client.gui.hud.setTimes(fadeIn, stay, fadeOut);
        client.gui.hud.setTitle(title);
        client.gui.hud.setSubtitle(subtitle);
    }
}
