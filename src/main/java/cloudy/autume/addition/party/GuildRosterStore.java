package cloudy.autume.addition.party;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Persists complete observed guild snapshots independently per Minecraft account. */
public final class GuildRosterStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOGGER = LoggerFactory.getLogger("QCloudy_Addition/Guild");
    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_ACCOUNTS = 32;

    private final Path file;
    private final Map<String, GuildRoster> loaded = new LinkedHashMap<>();
    private RosterFile data = new RosterFile();

    public GuildRosterStore(Path file) {
        this.file = file;
    }

    public static GuildRosterStore createDefault() {
        return new GuildRosterStore(FabricLoader.getInstance().getConfigDir()
                .resolve("qcloudy_addition_guild.json"));
    }

    public void load() {
        data = new RosterFile();
        loaded.clear();
        if (!Files.isRegularFile(file)) return;
        try {
            RosterFile parsed = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), RosterFile.class);
            if (parsed != null && parsed.schemaVersion == SCHEMA_VERSION && parsed.accounts != null) data = parsed;
        } catch (Exception exception) {
            LOGGER.warn("Could not read {}; starting with an empty guild roster", file, exception);
        }
    }

    public void observe(String accountKey, Component message) {
        GuildRoster roster = roster(accountKey);
        if (roster != null && roster.observe(message)) save(accountKey, roster);
    }

    public boolean isGuildMember(String accountKey, String username) {
        GuildRoster roster = roster(accountKey);
        return roster != null && roster.contains(username);
    }

    public void resetPendingSnapshots() {
        loaded.values().forEach(GuildRoster::resetPendingSnapshot);
    }

    GuildRoster roster(String accountKey) {
        String key = normalizeAccount(accountKey);
        if (key.isBlank()) return null;
        return loaded.computeIfAbsent(key, ignored -> {
            GuildRoster roster = new GuildRoster();
            AccountState state = data.accounts.get(key);
            if (state != null) roster.restore(state.known, state.members);
            return roster;
        });
    }

    private void save(String accountKey, GuildRoster roster) {
        String key = normalizeAccount(accountKey);
        if (key.isBlank() || roster == null) return;
        AccountState state = new AccountState();
        state.known = roster.isKnown();
        state.members = roster.serializedMembers();
        data.accounts.put(key, state);
        while (data.accounts.size() > MAX_ACCOUNTS) {
            String first = data.accounts.keySet().iterator().next();
            data.accounts.remove(first);
            loaded.remove(first);
        }
        write();
    }

    private void write() {
        try {
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temporary, GSON.toJson(data), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            LOGGER.warn("Could not save {}", file, exception);
        }
    }

    private static String normalizeAccount(String accountKey) {
        return accountKey == null ? "" : accountKey.trim().toLowerCase(Locale.ROOT);
    }

    @SuppressWarnings("unused")
    static final class RosterFile {
        int schemaVersion = SCHEMA_VERSION;
        Map<String, AccountState> accounts = new LinkedHashMap<>();
    }

    @SuppressWarnings("unused")
    static final class AccountState {
        boolean known;
        Map<String, String> members = new LinkedHashMap<>();
    }
}
