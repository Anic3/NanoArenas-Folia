package studio.resonos.nano.core.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import studio.resonos.nano.NanoArenas;
import studio.resonos.nano.core.arena.Arena;

/**
 * PlaceholderAPI expansion for NanoArenas.
 *
 * Usage: %nanoarenas_<type>_<arenaname>%
 *
 * Types:
 *   time    → HH:MM:SS (zero-padded)
 *   seconds → raw integer seconds
 *   short   → compact form, e.g. "1h 5m 30s" (skips zero units)
 *   hh      → hours component only (zero-padded)
 *   mm      → minutes component only (zero-padded)
 *   ss      → seconds component only (zero-padded)
 *   paused  → "true" or "false"
 *
 * Returns "N/A" when the arena does not exist or has no active countdown.
 */
public class NanoArenasExpansion extends PlaceholderExpansion {

    private final NanoArenas plugin;

    public NanoArenasExpansion(NanoArenas plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "nanoarenas";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Athishh";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String identifier) {
        // identifier format: <type>_<arenaname>
        int sep = identifier.indexOf('_');
        if (sep < 0) return null;

        String type = identifier.substring(0, sep);
        String arenaName = identifier.substring(sep + 1);

        Arena arena = Arena.getByName(arenaName);
        if (arena == null) return "N/A";

        if (type.equals("paused")) {
            return String.valueOf(arena.isAutoResetPaused());
        }

        int remaining = plugin.getResetScheduler().getRemainingSeconds(arena);
        if (remaining < 0) return "N/A";

        switch (type) {
            case "time":    return formatHMS(remaining);
            case "seconds": return String.valueOf(remaining);
            case "short":   return formatShort(remaining);
            case "hh":      return String.format("%02d", remaining / 3600);
            case "mm":      return String.format("%02d", (remaining % 3600) / 60);
            case "ss":      return String.format("%02d", remaining % 60);
            default:        return null;
        }
    }

    private static String formatHMS(int totalSeconds) {
        int h = totalSeconds / 3600;
        int m = (totalSeconds % 3600) / 60;
        int s = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }

    private static String formatShort(int totalSeconds) {
        int h = totalSeconds / 3600;
        int m = (totalSeconds % 3600) / 60;
        int s = totalSeconds % 60;
        StringBuilder sb = new StringBuilder();
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        sb.append(s).append("s");
        return sb.toString().trim();
    }
}
