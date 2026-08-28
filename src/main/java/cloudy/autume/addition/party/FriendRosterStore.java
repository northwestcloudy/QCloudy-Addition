package cloudy.autume.addition.party;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/** Persists observed friend classifications independently for each Minecraft account. */
public final class FriendRosterStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOGGER = LoggerFactory.getLogger("QCloudy_Addition/Friends");
    private static final int MAX_ACCOUNTS = 32;
    private static final int SCHEMA_VERSION = 2;
    private final Path file;
    private RosterFile data = new RosterFile();
    private final Map<String, FriendRoster> loaded = new LinkedHashMap<>();

    public FriendRosterStore(Path file) {
        this.file = file;
    }

    public static FriendRosterStore createDefault() {
        return new FriendRosterStore(FabricLoader.getInstance().getConfigDir()
                .resolve("qcloudy_addition_friends.json"));
    }

    public void load() {
        data = new RosterFile();
        loaded.clear();
        if (!Files.isRegularFile(file)) return;
        try {
            RosterFile parsed = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), RosterFile.class);
            if (parsed != null && parsed.accounts != null && parsed.schemaVersion <= SCHEMA_VERSION) {
                // Version 1 could mark a single observed page as complete. Keep its names only as
                // untrusted hints and require one proven-complete refresh before auto-accepting.
                if (parsed.schemaVersion < SCHEMA_VERSION) {
                    for (AccountState state : parsed.accounts.values()) {
                        if (state != null) state.known = false;
                    }
                }
                parsed.schemaVersion = SCHEMA_VERSION;
                data = parsed;
            }
        } catch (Exception exception) {
            LOGGER.warn("Could not read {}; starting with an empty friend roster", file, exception);
        }
    }

    public FriendRoster roster(String accountKey) {
        String key = accountKey == null ? "" : accountKey.trim().toLowerCase(java.util.Locale.ROOT);
        if (key.isBlank()) return new FriendRoster();
        return loaded.computeIfAbsent(key, ignored -> {
            FriendRoster result = new FriendRoster();
            AccountState state = data.accounts.get(key);
            if (state != null) result.restore(state.known, state.friends);
            return result;
        });
    }

    public void save(String accountKey, FriendRoster roster) {
        if (accountKey == null || accountKey.isBlank() || roster == null) return;
        String key = accountKey.trim().toLowerCase(java.util.Locale.ROOT);
        AccountState state = new AccountState();
        state.known = roster.isKnown();
        state.friends = roster.serializedFriends();
        data.accounts.put(key, state);
        while (data.accounts.size() > MAX_ACCOUNTS) {
            String first = data.accounts.keySet().iterator().next();
            data.accounts.remove(first);
            loaded.remove(first);
        }
        write();
    }

    /** Clears transient page transactions on disconnect without reviving an invalidated roster. */
    public void resetPendingSnapshots() {
        loaded.values().forEach(FriendRoster::resetPendingSnapshot);
    }

    private void write() {
        try {
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temporary, GSON.toJson(data), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            LOGGER.warn("Could not save {}", file, exception);
        }
    }

    @SuppressWarnings("unused")
    static final class RosterFile {
        int schemaVersion = SCHEMA_VERSION;
        Map<String, AccountState> accounts = new LinkedHashMap<>();
    }

    @SuppressWarnings("unused")
    static final class AccountState {
        boolean known;
        Map<String, String> friends = new LinkedHashMap<>();
    }
}
