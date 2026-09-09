package cloudy.autume.addition.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/** Pure responsive layout used by the chat-screen channel row. */
public final class ChatChannelLayout {
    public static final int HEIGHT = 16;
    public static final int GAP = 2;
    private static final int MINIMUM_WIDTH = 18;

    private ChatChannelLayout() {
    }

    public static List<Slot> calculate(int inputX, int inputY, int inputWidth,
                                      List<ChatChannel> channels,
                                      ToIntFunction<ChatChannel> preferredWidth,
                                      ToIntFunction<ChatChannel> compactWidth) {
        if (inputWidth <= 0 || channels == null || channels.isEmpty()) return List.of();
        int preferredTotal = total(channels, preferredWidth);
        boolean compact = preferredTotal > inputWidth;
        ToIntFunction<ChatChannel> selected = compact ? compactWidth : preferredWidth;
        int selectedTotal = total(channels, selected);
        if (selectedTotal > inputWidth) return List.of();

        int x = inputX;
        int y = Math.max(2, inputY - HEIGHT - GAP);
        List<Slot> result = new ArrayList<>(channels.size());
        for (ChatChannel channel : channels) {
            int width = Math.max(MINIMUM_WIDTH, selected.applyAsInt(channel));
            result.add(new Slot(channel, x, y, width, HEIGHT, compact));
            x += width + GAP;
        }
        return List.copyOf(result);
    }

    private static int total(List<ChatChannel> channels, ToIntFunction<ChatChannel> width) {
        int result = GAP * Math.max(0, channels.size() - 1);
        for (ChatChannel channel : channels) result += Math.max(MINIMUM_WIDTH, width.applyAsInt(channel));
        return result;
    }

    public record Slot(ChatChannel channel, int x, int y, int width, int height,
                       boolean compact) {
    }
}
