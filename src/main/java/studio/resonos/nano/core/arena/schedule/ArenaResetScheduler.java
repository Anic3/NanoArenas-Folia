package studio.resonos.nano.core.arena.schedule;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import studio.resonos.nano.NanoArenas;
import studio.resonos.nano.core.arena.Arena;
import studio.resonos.nano.core.util.CC;
import studio.resonos.nano.core.util.FoliaScheduler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Scheduler that manages per-arena countdowns and resets.
 * Compatible with both Folia and standard Paper/Spigot via {@link FoliaScheduler}.
 */
public class ArenaResetScheduler {

    private final JavaPlugin plugin;
    /** Holds the repeating {@link ScheduledTask} for each arena. */
    private final Map<String, ScheduledTask> taskHandles = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> remainingSeconds = new ConcurrentHashMap<>();

    public ArenaResetScheduler(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void scheduleAll() {
        cancelAll();
        if (!NanoArenas.get().getConfigManager().isResetEnabled()) {
            Bukkit.getConsoleSender().sendMessage(CC.translate(
                    "&8[&bNanoArenas&8] &eAuto-reset system is disabled in configuration."));
            return;
        }

        for (Arena arena : Arena.getArenas()) {
            schedule(arena);
        }
    }

    public void schedule(Arena arena) {
        if (arena == null) return;

        // Cancel any existing task for this arena.
        ScheduledTask existing = taskHandles.remove(arena.getName());
        if (existing != null) {
            existing.cancel();
            remainingSeconds.remove(arena.getName());
        }

        int resetSeconds = arena.getResetTime();
        if (resetSeconds <= 0) {
            if (NanoArenas.get().getConfigManager().isDebugMode()) {
                Bukkit.getConsoleSender().sendMessage(CC.translate(
                        "&8[&bNanoArenas&8] &fAuto-reset disabled for arena &b"
                                + arena.getName() + "&f (resetTime=" + resetSeconds + ")"));
            }
            return;
        }

        final int configuredSeconds = Math.max(1, resetSeconds);
        AtomicInteger remaining = new AtomicInteger(configuredSeconds);
        remainingSeconds.put(arena.getName(), remaining);

        long intervalTicks = NanoArenas.get().getConfigManager().getResetIntervalTicks();

        ScheduledTask task = FoliaScheduler.runTaskTimer(plugin, () -> {
            try {
                AtomicInteger rem = remainingSeconds.get(arena.getName());
                if (rem == null) return; // cancelled

                if (arena.isAutoResetPaused()) return;

                int value = rem.decrementAndGet();
                if (value <= 0) {
                    try {
                        if (NanoArenas.get().getConfigManager().isDebugMode()) {
                            Bukkit.getConsoleSender().sendMessage(CC.translate(
                                    "&8[&bNanoArenas&8] &fAuto-resetting arena &b"
                                            + arena.getName() + "&f now."));
                        }
                        arena.reset();
                        rem.set(configuredSeconds);
                    } catch (Exception e) {
                        Bukkit.getConsoleSender().sendMessage(CC.translate(
                                "&8[&bNanoArenas&8] &cFailed to reset arena &b"
                                        + arena.getName() + "&c. See console for details."));
                        e.printStackTrace();
                        rem.set(configuredSeconds);
                    }
                }
            } catch (Exception e) {
                Bukkit.getConsoleSender().sendMessage(CC.translate(
                        "&8[&bNanoArenas&8] &cError in arena countdown for arena &b"
                                + arena.getName() + ". See console for details."));
                e.printStackTrace();
            }
        }, intervalTicks, intervalTicks);

        taskHandles.put(arena.getName(), task);
    }

    public void cancel(Arena arena) {
        if (arena == null) return;
        ScheduledTask task = taskHandles.remove(arena.getName());
        if (task != null) {
            task.cancel();
        }
        remainingSeconds.remove(arena.getName());
    }

    public void cancelAll() {
        for (ScheduledTask task : taskHandles.values()) {
            if (task != null) task.cancel();
        }
        taskHandles.clear();
        remainingSeconds.clear();
    }

    /**
     * Returns remaining seconds until the next reset for {@code arena},
     * or {@code -1} if no countdown is scheduled.
     */
    public int getRemainingSeconds(Arena arena) {
        if (arena == null) return -1;
        AtomicInteger a = remainingSeconds.get(arena.getName());
        return a == null ? -1 : a.get();
    }
}