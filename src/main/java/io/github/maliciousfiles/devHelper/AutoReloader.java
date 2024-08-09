package io.github.maliciousfiles.devHelper;

import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.craftbukkit.v1_21_R1.CraftServer;
import org.bukkit.event.Event;
import org.bukkit.plugin.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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

        Object manager = Bukkit.getPluginManager();
        try {
            Field paperPluginManager = Bukkit.getServer().getClass().getDeclaredField("paperPluginManager");
            paperPluginManager.setAccessible(true);
            manager = paperPluginManager.get(Bukkit.getServer());

            Field instanceManager = manager.getClass().getDeclaredField("instanceManager");
            instanceManager.setAccessible(true);
            manager = instanceManager.get(manager);
        } catch (Throwable ignored) {}

        try {
            Field lookupNames = manager.getClass().getDeclaredField("lookupNames");
            lookupNames.setAccessible(true);

            Map<String, Plugin> value = (Map<String, Plugin>) lookupNames.get(manager);
            value.remove(plugin.getName().toLowerCase());
            plugin.getDescription().getProvides().forEach(p -> value.remove(p.toLowerCase()));

            Field plugins = manager.getClass().getDeclaredField("plugins");
            plugins.setAccessible(true);
            List<Plugin> list = (List<Plugin>) plugins.get(manager);
            list.remove(plugin);

            Field commandMap = manager.getClass().getDeclaredField("commandMap");
            commandMap.setAccessible(true);
            Field knownCommands = SimpleCommandMap.class.getDeclaredField("knownCommands");
            knownCommands.setAccessible(true);
            Map<String, Command> map = (Map<String, Command>) knownCommands.get(commandMap.get(manager));
            for (Map.Entry<String, Command> entry : List.copyOf(map.entrySet())) {
                if (entry.getValue() instanceof PluginCommand pc && pc.getPlugin().equals(plugin)) {
                    map.remove(entry.getKey());
                }
            }

            try {
                Field listenersField = manager.getClass().getDeclaredField("listeners");
                listenersField.setAccessible(true);
                Map<Event, SortedSet<RegisteredListener>> listeners = (Map<Event, SortedSet<RegisteredListener>>) listenersField.get(manager);
                listeners.values().forEach(set -> set.removeIf(listener -> listener.getPlugin().equals(plugin)));
            } catch (NoSuchFieldException ignored) {}

            if (plugin.getClass().getClassLoader() instanceof URLClassLoader ucl) ucl.close();

            if (manager.getClass().getSimpleName().contains("Paper")) {
                Class<?> clazz = Class.forName("io.papermc.paper.plugin.entrypoint.LaunchEntryPointHandler");
                Object instance = clazz.getDeclaredField("INSTANCE").get(null);
                Object storage = clazz.getDeclaredMethod("get", Class.forName("io.papermc.paper.plugin.entrypoint.Entrypoint")).invoke(instance, Class.forName("io.papermc.paper.plugin.entrypoint.Entrypoint").getField("PLUGIN").get(null));
                Field providersField = Class.forName("io.papermc.paper.plugin.storage.SimpleProviderStorage").getDeclaredField("providers");
                providersField.setAccessible(true);
                List providers = (List) providersField.get(storage);
                providers.removeIf(obj -> {
                    try {
                        Object meta = Class.forName("io.papermc.paper.plugin.provider.PluginProvider")
                                .getDeclaredMethod("getMeta").invoke(obj);
                        Method getName = Class.forName("io.papermc.paper.plugin.configuration.PluginMeta")
                                .getDeclaredMethod("getName");
                        getName.setAccessible(true);
                        return getName.invoke(meta).equals(plugin.getName());
                    } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException |
                             ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        } catch (NoSuchFieldException | IllegalAccessException | IOException | ClassNotFoundException |
                 NoSuchMethodException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }

        Bukkit.getBossBars().forEachRemaining(b -> {
            if (b.getKey().getNamespace().equalsIgnoreCase(plugin.getName())) {
                Bukkit.removeBossBar(b.getKey());
            }
        });
        Bukkit.recipeIterator().forEachRemaining(r -> {
            if (r instanceof Keyed keyed && keyed.getKey().getNamespace().equalsIgnoreCase(plugin.getName())) {
                Bukkit.removeRecipe(keyed.getKey());
            }
        });

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
                    md5Hashes.put(file, hash(file));

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
