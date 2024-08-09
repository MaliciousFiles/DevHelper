package io.github.maliciousfiles.devHelper;

import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_21_R1.CraftServer;
import org.bukkit.plugin.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URLClassLoader;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.nio.file.StandardWatchEventKinds.*;

public class AutoReloader {

    private static final File directory = new File(".", "plugins");
    private static WatchService watchService;
    private static WatchKey watchKey;

    private static final PluginLoader pluginLoader = DevHelper.instance.getPluginLoader();
    private static final Map<File, Plugin> pluginFiles = new HashMap<>();

    public static void init() {
        try {
            Field fileField = JavaPlugin.class.getDeclaredField("file");
            fileField.setAccessible(true);

            for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
                if (plugin instanceof JavaPlugin javaPlugin) {
                    pluginFiles.put((File) fileField.get(javaPlugin), javaPlugin);
                }
            }

            watchService = directory.toPath().getFileSystem().newWatchService();
            watchKey = directory.toPath().register(watchService, ENTRY_MODIFY, ENTRY_CREATE, ENTRY_DELETE);

            Bukkit.getScheduler().runTaskAsynchronously(DevHelper.instance, AutoReloader::check);
        } catch (NoSuchFieldException | IllegalAccessException | IOException e) { throw new RuntimeException(e); }
    }

    private static Plugin load(File file) throws InvalidPluginException, InvalidDescriptionException {
        Plugin plugin = Bukkit.getPluginManager().loadPlugin(file);
        plugin.onLoad();
        Bukkit.getPluginManager().enablePlugin(plugin);

        ((CraftServer) Bukkit.getServer()).syncCommands();

        return plugin;
    }

    private static void unload(Plugin plugin) {
        if (plugin == null) return;

        Bukkit.getPluginManager().disablePlugin(plugin);

        SimplePluginManager manager = (SimplePluginManager) Bukkit.getPluginManager();
        try {
            Field lookupNames = manager.getClass().getDeclaredField("lookupNames");
            lookupNames.setAccessible(true);

            Map<String, Plugin> value = (Map<String, Plugin>) lookupNames.get(manager);
            value.remove(plugin.getName());
            plugin.getDescription().getProvides().forEach(value::remove);

            Field plugins = manager.getClass().getDeclaredField("plugins");
            plugins.setAccessible(true);

            ((List<Plugin>) plugins.get(manager)).remove(plugin);

            if (plugin.getClass().getClassLoader() instanceof URLClassLoader ucl) ucl.close();
        } catch (NoSuchFieldException | IllegalAccessException | IOException e) {
            throw new RuntimeException(e);
        }

        ((CraftServer) Bukkit.getServer()).syncCommands();

        System.gc();
    }

    private static void check() {
        while (true) {
            try {
                WatchKey key = watchService.take();

                for (WatchEvent<?> event : key.pollEvents()) {
                    File file = new File(directory, event.context().toString());

                    if (event.kind() == ENTRY_CREATE) {
                        try {
                            pluginFiles.put(file, load(file));
                        } catch (InvalidPluginException | InvalidDescriptionException ignored) {}
                    } else if (event.kind() == ENTRY_DELETE) {
                        unload(pluginFiles.remove(file));
                    } else if (event.kind() == ENTRY_MODIFY) {
                        unload(pluginFiles.get(file));
                        try {
                            pluginFiles.put(file, load(file));
                        } catch (InvalidPluginException | InvalidDescriptionException ignored) {}
                    }
                }
            } catch (InterruptedException e) { return; }
        }
    }
}
