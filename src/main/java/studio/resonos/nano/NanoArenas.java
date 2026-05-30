package studio.resonos.nano;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import studio.resonos.nano.core.util.FoliaScheduler;
import studio.resonos.nano.api.command.CommandHandler;
import studio.resonos.nano.api.gui.SpiGUI;
import studio.resonos.nano.core.arena.Arena;
import studio.resonos.nano.core.arena.listener.ArenaResetBroadcastListener;
import studio.resonos.nano.core.arena.schedule.ArenaResetScheduler;
import studio.resonos.nano.core.managers.AdminAlertManager;
import studio.resonos.nano.core.placeholder.NanoArenasExpansion;
import studio.resonos.nano.core.util.CC;
import studio.resonos.nano.core.util.Config;
import studio.resonos.nano.core.util.ConfigurationManager;
import studio.resonos.nano.core.util.file.type.BasicConfigurationFile;

/**
 * @Author Athulsib
 * Package: studio.resonos.arenas.core.arena.generator
 * Created on: 12/16/2023
 */

@Getter
@Setter
public class NanoArenas extends JavaPlugin {

    public static SpiGUI spiGUI;
    private static NanoArenas nanoArenas;
    @Getter
    private BasicConfigurationFile arenasConfig;
    public Config mainConfig;
    @Getter
    private ConfigurationManager configManager;

    public static NanoArenas get() {
        return nanoArenas;
    }
    private ArenaResetScheduler resetScheduler;
    private AdminAlertManager manager;

    @Override
    public void onEnable() {
        String str = """
                  _   _          _   _  ____ \s
                 | \\ | |   /\\   | \\ | |/ __ \\\s
                 |  \\| |  /  \\  |  \\| | |  | |
                 | . ` | / /\\ \\ | . ` | |  | |
                 | |\\  |/ ____ \\| |\\  | |__| |
                 |_| \\_/_/    \\_\\_| \\_|\\____/\s
                
                
                """;
        Bukkit.getConsoleSender().sendMessage(CC.CHAT_BAR);
        //Bukkit.getConsoleSender().sendMessage(CC.translate(" &b&lNano Arenas"));
        Bukkit.getConsoleSender().sendMessage(CC.translate("&b  _   _          _   _  ____  "));
        Bukkit.getConsoleSender().sendMessage(CC.translate("&b  | \\ | |   /\\   | \\ | |/ __ \\ "));
        Bukkit.getConsoleSender().sendMessage(CC.translate("&b  |  \\| |  /  \\  |  \\| | |  | |"));
        Bukkit.getConsoleSender().sendMessage(CC.translate("&b  | . ` | / /\\ \\ | . ` | |  | |"));
        Bukkit.getConsoleSender().sendMessage(CC.translate("&b  | |\\  |/ ____ \\| |\\  | |__| |"));
        Bukkit.getConsoleSender().sendMessage(CC.translate("&b  |_| \\_/_/    \\_\\_| \\_|\\____/ "));
        Bukkit.getConsoleSender().sendMessage(CC.translate("&b "));
        Bukkit.getConsoleSender().sendMessage(CC.translate((" &b▐ &fAuthor&7: &bAthishh")));
        Bukkit.getConsoleSender().sendMessage(CC.translate((" &b▐ &fVersion &7: &b" + getDescription().getVersion())));
        Bukkit.getConsoleSender().sendMessage(CC.translate(" "));
        Bukkit.getConsoleSender().sendMessage(CC.translate(" &aThank you for Using Nano Arenas!"));
        Bukkit.getConsoleSender().sendMessage(CC.translate(" &aJoin our discord for support &b&ndsc.gg/resonos"));
        Bukkit.getConsoleSender().sendMessage(CC.CHAT_BAR);
        nanoArenas = this;

        // Initialize configuration manager first
        configManager = new ConfigurationManager(this);

        arenasConfig = new BasicConfigurationFile(this, "arenas");
        spiGUI = new SpiGUI(this);
        manager = new AdminAlertManager();
        Bukkit.getServer().getPluginManager().registerEvents(new ArenaResetBroadcastListener(manager), this);
        resetScheduler = new ArenaResetScheduler(this);
        registerProcessors();
        registerCommands();

        // Defer arena loading until ServerLoadEvent so that world-loading plugins
        // (e.g. MoreFoWorld, Multiverse) have finished loading all custom worlds
        // before we try to deserialize arena locations.
        Bukkit.getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onServerLoad(ServerLoadEvent event) {
                Arena.init();
                NanoArenas.get().getLogger().info("Started Reset timer Task");
                resetScheduler.scheduleAll();
                if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                    new NanoArenasExpansion(NanoArenas.get()).register();
                    NanoArenas.get().getLogger().info("PlaceholderAPI expansion registered.");
                }
            }
        }, this);
    }


    @Override
    public void onDisable() {
        // cancel scheduler tasks
        if (resetScheduler != null) {
            resetScheduler.cancelAll();
        }

        // Persist every arena. Guard each save so one failure does not prevent
        // the remaining arenas from being written. We intentionally do NOT call
        // reset() here: resetting pastes schematics / uses region schedulers that
        // are being torn down during disable, which would throw and abort saving.
        for (Arena arena : Arena.getArenas()) {
            try {
                arena.save();
            } catch (Exception e) {
                getLogger().severe("Failed to save arena '" + arena.getName()
                        + "' on shutdown: " + e.getMessage());
            }
        }
    }

    private void registerProcessors() {
        CommandHandler.registerProcessors("studio.resonos.nano.api.command.processors", this);
    }

    private void registerCommands() {
        CommandHandler.registerCommands("studio.resonos.nano.core.commands.arena", this);
        CommandHandler.registerCommands("studio.resonos.nano.core.commands.dev", this);
    }

    /**
     * Reloads the plugin configuration
     */
    public void reloadConfiguration() {
        configManager.reloadConfig();
        // Reschedule all arena resets with new configuration
        if (resetScheduler != null) {
            resetScheduler.scheduleAll();
        }
    }

}