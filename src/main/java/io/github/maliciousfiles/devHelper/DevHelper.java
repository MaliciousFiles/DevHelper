package io.github.maliciousfiles.devHelper;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class DevHelper extends JavaPlugin {

    public static DevHelper instance;

    @Override
    public void onEnable() {
        instance = this;

        getCommand("console").setExecutor(new ConsoleGrabber());
        getCommand("console").setTabCompleter(new ConsoleGrabber());

        Bukkit.getScheduler().runTask(this, AutoReloader::init);

        Bukkit.getOperators().forEach(op -> { if (op.isOnline()) op.getPlayer().performCommand("console WARN"); });
    }

    @Override
    public void onDisable() {
        ConsoleGrabber.removeAll();
        AutoReloader.unload.run();
    }
}
