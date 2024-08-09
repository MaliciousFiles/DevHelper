package io.github.maliciousfiles.devHelper;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LifeCycle;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.message.Message;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ConsoleGrabber /*extends Handler*/ implements CommandExecutor, TabCompleter {

    private static final Map<UUID, ConsoleFilter> filters = new HashMap<>();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player) {
            if (filters.containsKey(player.getUniqueId())) {
                ((Logger) LogManager.getRootLogger()).get().removeFilter(filters.get(player.getUniqueId()));
                filters.remove(player.getUniqueId());

                player.sendMessage("§6Unsubscribed from console output");
            } else {
                org.apache.logging.log4j.Level level = args.length > 0 ? org.apache.logging.log4j.Level.getLevel(args[0].toUpperCase()) : org.apache.logging.log4j.Level.ALL;
                ConsoleFilter filter = new ConsoleFilter(player, level);

                ((Logger) LogManager.getRootLogger()).get().addFilter(filter);
                filters.put(player.getUniqueId(), filter);

                player.sendMessage("§6Subscribed to console output at level §b"+level.name());
            }
        } else {
            sender.sendMessage("Only players can run this command");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (sender instanceof Player player && !filters.containsKey(player.getUniqueId()) && args.length == 1) {
            return Arrays.stream(org.apache.logging.log4j.Level.values()).sorted().map(org.apache.logging.log4j.Level::name).filter(s -> s.startsWith(args[0].toUpperCase())).toList();
        }

        return new ArrayList<>();
    }

    public static void removeAll() {
        filters.values().forEach(((Logger) LogManager.getRootLogger()).get()::removeFilter);
        filters.clear();
    }

    private static class ConsoleFilter implements Filter {
        private final Player player;
        private final org.apache.logging.log4j.Level level;

        public ConsoleFilter(Player player, org.apache.logging.log4j.Level level) {
            this.player = player;
            this.level = level;
        }

        @Override
        public Filter.Result getOnMismatch () {
            return null;
        }

        @Override
        public Filter.Result getOnMatch () {
            return null;
        }

        @Override
        public Filter.Result filter (Logger logger, org.apache.logging.log4j.Level level, Marker marker, String
                msg, Object...params){
            filter(level, msg);
            return null;
        }

        @Override
        public Filter.Result filter (Logger logger, org.apache.logging.log4j.Level level, Marker marker, String
                message, Object p0){
            filter(level, message);
            return null;
        }

        @Override
        public Filter.Result filter (Logger logger, org.apache.logging.log4j.Level level, Marker marker, String
                message, Object p0, Object p1){
            filter(level, message);
            return null;
        }

        @Override
        public Filter.Result filter (Logger logger, org.apache.logging.log4j.Level level, Marker marker, String
                message, Object p0, Object p1, Object p2){
            filter(level, message);
            return null;
        }

        @Override
        public Filter.Result filter (Logger logger, org.apache.logging.log4j.Level level, Marker marker, String
                message, Object p0, Object p1, Object p2, Object p3){
            filter(level, message);
            return null;
        }

        @Override
        public Filter.Result filter (Logger logger, org.apache.logging.log4j.Level level, Marker marker, String
                message, Object p0, Object p1, Object p2, Object p3, Object p4){
            filter(level, message);
            return null;
        }

        @Override
        public Filter.Result filter (Logger logger, org.apache.logging.log4j.Level level, Marker marker, String
                message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5){
            filter(level, message);
            return null;
        }

        @Override
        public Filter.Result filter (Logger logger, org.apache.logging.log4j.Level level, Marker marker, String
                message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6){
            filter(level, message);
            return null;
        }

        @Override
        public Filter.Result filter (Logger logger, org.apache.logging.log4j.Level level, Marker marker, String
                message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7){
            filter(level, message);
            return null;
        }

        @Override
        public Filter.Result filter (Logger logger, org.apache.logging.log4j.Level level, Marker marker, String
                message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8){
            filter(level, message);
            return null;
        }

        @Override
        public Filter.Result filter (Logger logger, org.apache.logging.log4j.Level level, Marker marker, String
                message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object
                                             p8, Object p9){
            filter(level, message);
            return null;
        }

        @Override
        public Filter.Result filter (Logger logger, org.apache.logging.log4j.Level level, Marker marker, Object
                msg, Throwable t){
            filter(level, msg+": "+t.getMessage());
            return null;
        }

        @Override
        public Filter.Result filter (Logger logger, org.apache.logging.log4j.Level level, Marker marker, Message
                msg, Throwable t){
            filter(level, msg+": "+t.getMessage());

            return null;
        }

        @Override
        public Filter.Result filter (LogEvent event) {
            String msg = event.getMessage().getFormattedMessage();
            if (event.getThrown() != null) {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                event.getThrown().printStackTrace(pw);

                List<String> pluginPackages = Arrays.stream(Bukkit.getPluginManager().getPlugins())
                        .map(p -> p.getClass().getPackageName()).toList();

                String stackTrace = sw.toString();
                stackTrace = Arrays.stream(Arrays.stream(stackTrace.split("\n"))
                                .filter(s -> pluginPackages.stream().anyMatch(s::contains))
                                .collect(Collectors.joining("\n"))
                                .replaceAll("%s\\.[a-z0-9.]*".formatted("("+String.join("|", pluginPackages
                                        .stream().map(s -> s.replace(".", "\\."))
                                        .toList())+")"), "")
                                .replace("\t", "    ")
                                .replace("\r", "")
                                .replace("at ", "")
                                .split("\n"))
                        .map(s -> {
                            String newS = s.replaceAll("\\([a-zA-Z0-9]*\\.java:[0-9]*\\)", "");
                            String spaces = " ".repeat(Math.max(0, 45 - newS.length()));

                            return s.replaceAll("\\([a-zA-Z0-9]*\\.java:([0-9]*)\\)", spaces+"$1");
                        })
                        .collect(Collectors.joining("\n"));

                msg = event.getThrown().getMessage()+": "+msg+"\n"+stackTrace;
            }

            filter(event.getLevel(), msg);

            return null;
        }

        private void filter(org.apache.logging.log4j.Level level, String message) {
            if (level.isMoreSpecificThan(this.level)) {
                String prefix;

                if (level.isMoreSpecificThan(org.apache.logging.log4j.Level.ERROR)) prefix = "§4[" + level.name() + "] §r§c";
                else if (level == org.apache.logging.log4j.Level.WARN) prefix = "§6[" + level.name() + "] §r§e";
                else prefix = "§7[" + level.name() + "] §r";

                player.sendMessage(prefix + message);
            }
        }

        @Override
        public LifeCycle.State getState () {
            return null;
        }

        @Override
        public void initialize () {

        }

        @Override
        public void start () {

        }

        @Override
        public void stop () {

        }

        @Override
        public boolean isStarted () {
            return false;
        }

        @Override
        public boolean isStopped () {
            return false;
        }
    }
}
