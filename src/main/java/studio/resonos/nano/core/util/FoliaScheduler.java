package studio.resonos.nano.core.util;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

/**
 * Scheduler utility that works on both Folia and standard Paper/Spigot.
 * <p>
 * Uses Paper's new scheduler APIs ({@code GlobalRegionScheduler},
 * {@code AsyncScheduler}, {@code EntityScheduler}) which are present on
 * Paper 1.19.4+ and delegate correctly to the Bukkit scheduler on non-Folia
 * builds while using region-specific threading on Folia.
 * <p>
 * Folia is detected at class-load time via the presence of
 * {@code io.papermc.paper.threadedregions.RegionizedServer}.
 */
public final class FoliaScheduler {

    /** {@code true} when the server is running Folia. */
    public static final boolean IS_FOLIA;

    static {
        boolean folia;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (ClassNotFoundException e) {
            folia = false;
        }
        IS_FOLIA = folia;
    }

    private FoliaScheduler() {}

    // -------------------------------------------------------------------------
    // Global-region / main-thread equivalents
    // -------------------------------------------------------------------------

    /**
     * Runs {@code task} on the global region (main thread on Paper, global
     * region thread on Folia) as soon as possible.
     */
    public static void runTask(Plugin plugin, Runnable task) {
        Bukkit.getGlobalRegionScheduler().run(plugin, t -> task.run());
    }

    /**
     * Runs {@code task} after {@code delayTicks} ticks on the global region.
     * The delay is clamped to a minimum of 1 tick.
     */
    public static void runTaskLater(Plugin plugin, Runnable task, long delayTicks) {
        Bukkit.getGlobalRegionScheduler()
                .runDelayed(plugin, t -> task.run(), Math.max(1L, delayTicks));
    }

    /**
     * Schedules a repeating {@code task} on the global region and returns the
     * {@link ScheduledTask} handle so it can be cancelled later.
     *
     * @param initialDelayTicks ticks before the first execution (min 1)
     * @param periodTicks       ticks between subsequent executions (min 1)
     */
    public static ScheduledTask runTaskTimer(Plugin plugin, Runnable task,
                                             long initialDelayTicks, long periodTicks) {
        return Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                plugin,
                t -> task.run(),
                Math.max(1L, initialDelayTicks),
                Math.max(1L, periodTicks));
    }

    // -------------------------------------------------------------------------
    // Async
    // -------------------------------------------------------------------------

    /**
     * Runs {@code task} asynchronously (off the main/region thread).
     */
    public static void runTaskAsync(Plugin plugin, Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin, t -> task.run());
    }

    // -------------------------------------------------------------------------
    // Entity-safe teleportation
    // -------------------------------------------------------------------------

    /**
     * Teleports {@code entity} to {@code location} safely.
     * <p>
     * On Folia, entity operations must be performed on the entity's own region
     * thread; this method schedules the teleport via {@link Entity#getScheduler()}.
     * On standard Paper the entity scheduler also works correctly.
     *
     * @param retired optional {@link Runnable} called when the entity is removed
     *                before the task executes; may be {@code null}
     */
    public static void teleportEntity(Plugin plugin, Entity entity, Location location,
                                      Runnable retired) {
        entity.getScheduler().run(plugin, t -> entity.teleport(location), retired);
    }

    /** Convenience overload with no retired callback. */
    public static void teleportEntity(Plugin plugin, Entity entity, Location location) {
        teleportEntity(plugin, entity, location, null);
    }
}