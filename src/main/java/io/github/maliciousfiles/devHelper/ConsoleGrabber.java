package io.github.maliciousfiles.devHelper;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.Stream;

public class ConsoleGrabber extends Handler implements CommandExecutor, TabCompleter {

    private static final Map<Level, ChatColor[]> colors = Map.of(
            Level.OFF, new ChatColor[] {ChatColor.GRAY, ChatColor.DARK_GRAY},
            Level.SEVERE, new ChatColor[] {ChatColor.RED, ChatColor.DARK_RED},
            Level.WARNING, new ChatColor[] {ChatColor.YELLOW, ChatColor.GOLD},
            Level.INFO, new ChatColor[] {ChatColor.WHITE, ChatColor.GRAY},
            Level.CONFIG, new ChatColor[] {ChatColor.AQUA, ChatColor.DARK_AQUA},
            Level.FINE, new ChatColor[] {ChatColor.GRAY, ChatColor.DARK_GRAY},
            Level.FINER, new ChatColor[] {ChatColor.GRAY, ChatColor.DARK_GRAY},
            Level.FINEST, new ChatColor[] {ChatColor.GRAY, ChatColor.DARK_GRAY},
            Level.ALL, new ChatColor[] {ChatColor.GRAY, ChatColor.DARK_GRAY}
    );


    private static final Map<UUID, Level> levels = new HashMap<>();

    @Override
    public void publish(LogRecord record) {
        levels.forEach((uuid, l) -> {
            if (l.intValue() <= record.getLevel().intValue()) {
                Optional.ofNullable(Bukkit.getPlayer(uuid)).ifPresent(p -> {
                    ChatColor[] colors = ConsoleGrabber.colors.get(record.getLevel());
                    p.sendMessage("%s[%s%s%s] %s%s".formatted(
                            colors[1], colors[0], record.getLevel().getName(), colors[1], colors[0], record.getMessage())
                    );
                });
            }
        });
    }
    public void flush() {}
    public void close() throws SecurityException {}

    private static final List<Level> levelList = List.of(Level.OFF, Level.SEVERE,
            Level.WARNING, Level.INFO, Level.CONFIG, Level.FINE, Level.FINER, Level.FINEST, Level.ALL);

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] strings) {
        if (!(commandSender instanceof Player p)) {
            commandSender.sendMessage("§cYou must be a player to use this command");
            return true;
        }

        try {
            Level level = Level.parse(strings[0].toUpperCase());

            levels.put(p.getUniqueId(), level);
            p.sendMessage("§bLog level set to §6" + level.getName());
        } catch (IllegalArgumentException ignored) {
            p.sendMessage("§cInvalid log level");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender commandSender, Command command, String label, String[] strings) {
        if (!(commandSender instanceof Player)) return List.of();

        if (strings.length > 1) return List.of();

        List<String> ret;
        try {
            Integer.parseInt(strings[0]);
            ret = levelList.stream().map(l -> String.valueOf(l.intValue())).toList();
        } catch (NumberFormatException ignored) {
            ret = levelList.stream().map(Level::getName).toList();

        }
        return ret.stream().filter(s -> s.startsWith(strings[0].toUpperCase())).sorted().toList();
    }
}
