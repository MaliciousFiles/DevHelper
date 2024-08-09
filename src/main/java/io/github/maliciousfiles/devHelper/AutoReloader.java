package io.github.maliciousfiles.devHelper;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_21_R1.CraftServer;
import org.bukkit.plugin.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URLClassLoader;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.logging.Level;

import static java.nio.file.StandardWatchEventKinds.*;

public class AutoReloader {

    private static final File directory = new File(".", "plugins");

    private static final Map<File, Plugin> pluginFiles = new HashMap<>();
    private static final Map<File, byte[]> md5Hashes = new HashMap<>();

    public static Runnable unload;

    private static byte[] hash(File file) {
        try(FileInputStream fs = new FileInputStream(file)) {
            return MessageDigest.getInstance("md5").digest(fs.readAllBytes());
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void init() {
        for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
            File file = Arrays.stream(directory.listFiles())
                    .filter(f -> {
                        try {
                            return DevHelper.instance.getPluginLoader()
                                    .getPluginDescription(f).getName().equals(plugin.getName());
                        } catch (InvalidDescriptionException e) { return false; }
                    }).findFirst().orElse(null);

            pluginFiles.put(file, plugin);
            md5Hashes.put(file, hash(file));
        }

        unload = Bukkit.getScheduler().runTaskTimer(DevHelper.instance, AutoReloader::check, 10, 40)::cancel;
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

    private static void log(String message) {
        List<CommandSender> potential = new ArrayList<>(Bukkit.getOnlinePlayers());
        potential.add(Bukkit.getConsoleSender());

        potential.stream().filter(CommandSender::isOp).forEach(p -> p.sendMessage(message));
    }

    private static void check() {
        List<Plugin> toUnload = new ArrayList<>(pluginFiles.values());
        for (File file : directory.listFiles()) {
            if (file.getName().toLowerCase().endsWith(".jar") && !pluginFiles.containsKey(file)) {
                try {
                    log("§6Loading §b%s".formatted(file.getName()));

                    Plugin plugin = load(file);
                    pluginFiles.put(file, plugin);
                    md5Hashes.put(file, hash(file));
                } catch (InvalidPluginException | InvalidDescriptionException e) { continue; }
            }

            if (md5Hashes.containsKey(file)) {
                Plugin plugin = pluginFiles.get(file);
                toUnload.remove(plugin);

                byte[] hash = hash(file);
                if (!Arrays.equals(hash, md5Hashes.get(file))) {
                    log("§6Reloading §b%s".formatted(plugin.getName()));

                    unload(plugin);
                    try {
                        load(file);
                    } catch (InvalidPluginException | InvalidDescriptionException e) { continue; }
                }
            }
        }

        for (Plugin plugin : toUnload) {
            log("§6Unloading §b%s".formatted(plugin.getName()));
            unload(plugin);

            File file = pluginFiles.entrySet().stream().filter(e -> e.getValue().equals(plugin)).findFirst().orElseThrow().getKey();
            pluginFiles.remove(file);
            md5Hashes.remove(file);
        }
    }
}
