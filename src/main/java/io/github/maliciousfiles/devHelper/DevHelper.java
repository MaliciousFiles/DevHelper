package io.github.maliciousfiles.devHelper;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class DevHelper extends JavaPlugin implements Listener {

    public static DevHelper instance;

    @Override
    public void onEnable() {
        instance = this;

        getCommand("console").setExecutor(new ConsoleGrabber());
        getCommand("console").setTabCompleter(new ConsoleGrabber());

        Bukkit.getScheduler().runTask(this, AutoReloader::init);

        Bukkit.getOperators().forEach(op -> { if (op.isOnline()) ConsoleGrabber.autoSubscribe(op.getPlayer()); });
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (e.getPlayer().isOp()) ConsoleGrabber.autoSubscribe(e.getPlayer());
    }

    @Override
    public void onDisable() {
        ConsoleGrabber.removeAll();
        AutoReloader.unload.run();
    }
}
