package me.Domplanto.streamLabs;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.Domplanto.streamLabs.config.ActionPlaceholder;
import me.Domplanto.streamLabs.config.RewardsConfig;
import me.Domplanto.streamLabs.events.StreamlabsEvent;
import me.Domplanto.streamLabs.exception.UnexpectedJsonFormatException;
import me.Domplanto.streamLabs.socket.StreamlabsSocketClient;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.java_websocket.handshake.ServerHandshake;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

public class StreamLabs extends JavaPlugin {
    private static final Set<? extends StreamlabsEvent> STREAMLABS_EVENTS = StreamlabsEvent.findEventClasses();
    private StreamlabsSocketClient socketClient;
    private RewardsConfig rewardsConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        FileConfiguration config = getConfig();
        String socketToken = config.getString("streamlabs.socket_token", "");
        this.rewardsConfig = new RewardsConfig(config);
        if (socketToken.isEmpty()) {
            getLogger().warning("Streamlabs socket token not configured!");
            getLogger().warning("Please set your token in config.yml");
            return;
        }

        this.socketClient = new StreamlabsSocketClient(socketToken, getLogger(), this::handleStreamlabsEvent)
                .setConnectionOpenListener(this::onConnectionOpen)
                .setConnectionCloseListener(this::onConnectionClosed)
                .setInvalidTokenListener(this::onInvalidSocketToken);
        this.socketClient.connect();
    }

    private void onConnectionOpen(ServerHandshake handshake) {
        Bukkit.broadcastMessage(ChatColor.GREEN + "Successfully connected to Streamlabs!");
    }

    private void onConnectionClosed(String message) {
        Bukkit.broadcastMessage(ChatColor.RED + "Connection to Streamlabs lost!");
    }

    private void onInvalidSocketToken() {
        Bukkit.broadcastMessage(ChatColor.YELLOW + "The socket token specified is invalid!");
    }

    @Override
    public void onDisable() {
        if (socketClient != null && socketClient.isOpen()) {
            socketClient.close();
        }
    }

    private void handleStreamlabsEvent(JsonElement data) throws UnexpectedJsonFormatException {
        JsonObject object = data.getAsJsonArray().get(1).getAsJsonObject();
        String type = object.get("type").getAsString();
        String platform = object.has("for") ? object.get("for").getAsString() : "streamlabs";

        StreamlabsEvent event = STREAMLABS_EVENTS.stream()
                .filter(e -> e.getApiName().equals(type) && e.getPlatform().compare(platform))
                .findFirst().orElse(null);
        if (event == null) return;


        JsonObject baseObject = event.getBaseObject(object);
        String message = event.getMessage(baseObject);
        if (message != null && !message.isEmpty()) {
            Bukkit.broadcastMessage(message);
        }

        List<RewardsConfig.Action> actions = rewardsConfig.getActionsForEvent(event.getId());
        for (RewardsConfig.Action action : actions) {
            if (!action.isEnabled()) continue;

            if (event.checkConditions(action, baseObject)) {
                executeAction(action, event, baseObject);
            }
        }
    }

    private void executeAction(RewardsConfig.Action action, StreamlabsEvent event, JsonObject baseObject) {
        for (String command : action.getCommands()) {
            // Replace placeholders
            for (ActionPlaceholder placeholder : event.getPlaceholders()) {
                command = command.replace(String.format("{%s}", placeholder.name()),
                        placeholder.valueFunction().apply(baseObject));
            }

            List<String> players = command.contains("{player}") ?
                    getConfig().getStringList("affected_players") : List.of("");
            for (String player : players) {
                String finalCommand = command.replace("{player}", player);
                Bukkit.getScheduler().runTask(this, () ->
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand));
            }
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, Command command, @NotNull String label, String[] args) {
        if (command.getName().equalsIgnoreCase("streamlabs")) {
            if (!sender.hasPermission("streamlabs.admin")) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
                return true;
            }

            if (args.length >= 1) {
                switch (args[0].toLowerCase()) {
                    case "connect":
                        if (!socketClient.isOpen()) {
                            this.socketClient.reconnect();
                            sender.sendMessage(ChatColor.GREEN + "Connecting to Streamlabs...");
                        } else {
                            sender.sendMessage(ChatColor.YELLOW + "Already connected to Streamlabs!");
                        }
                        return true;

                    case "disconnect":
                        if (socketClient.isOpen() && socketClient != null) {
                            socketClient.close();
                            sender.sendMessage(ChatColor.RED + "Disconnected from Streamlabs!");
                        } else {
                            sender.sendMessage(ChatColor.YELLOW + "Not connected to Streamlabs!");
                        }
                        return true;

                    case "status":
                        sender.sendMessage(ChatColor.BLUE + "Streamlabs Status: " +
                                (socketClient.isOpen() ? ChatColor.GREEN + "Connected" : ChatColor.RED + "Disconnected"));
                        return true;

                    case "reload":
                        reloadConfig();
                        rewardsConfig = new RewardsConfig(getConfig());
                        socketClient.reconnect();
                        sender.sendMessage(ChatColor.GREEN + "Configuration reloaded!");
                        return true;

                    case "player":
                        if (args.length != 3) {
                            sender.sendMessage(ChatColor.RED + "Please specify a player name");
                            return true;
                        }

                        FileConfiguration config = getConfig();
                        List<String> players = config.getStringList("affected_players");
                        if (args[1].equals("add")) {
                            players.add(args[2]);
                            sender.sendMessage(ChatColor.GREEN + String.format("%s added to affected players", args[2]));
                        } else if (args[1].equals("remove")) {
                            players.removeIf(player -> player.equals(args[2]));
                            sender.sendMessage(ChatColor.GREEN + String.format("%s removed from affected players", args[2]));
                        } else {
                            sender.sendMessage(ChatColor.RED + String.format("Unknown sub-command \"%s\"", args[1]));
                        }

                        getConfig().set("affected_players", players);
                        saveConfig();
                        return true;
                }
            }
        }
        return false;
    }
}
