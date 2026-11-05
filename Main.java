package com.kwadratowe.gildie;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class Main extends JavaPlugin implements CommandExecutor, Listener {

    public static class Guild {
        String name;
        String tag;
        String leader;
        ChatColor color;
        Location center;
        Set<String> members = new HashSet<>();

        public Guild(String name, String tag, String leader, ChatColor color, Location center) {
            this.name = name;
            this.tag = tag;
            this.leader = leader;
            this.color = color;
            this.center = center;
            this.members.add(leader);
        }
    }

    private final Map<String, Guild> guilds = new HashMap<>();
    private final Map<String, String> playerGuilds = new HashMap<>();
    private final Map<String, String> invites = new HashMap<>();

    private final List<ChatColor> availableColors = Arrays.asList(
            ChatColor.RED, ChatColor.BLUE, ChatColor.GREEN, ChatColor.YELLOW,
            ChatColor.GOLD, ChatColor.LIGHT_PURPLE, ChatColor.AQUA, ChatColor.DARK_PURPLE,
            ChatColor.DARK_RED, ChatColor.DARK_BLUE, ChatColor.DARK_GREEN
    );

    private final int PROTECTION_RADIUS = 25;
    private final int DIAMOND_COST = 5;

    private File file;
    private FileConfiguration config;

    @Override
    public void onEnable() {
        getCommand("g").setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);

        createFile();
        loadGuilds();

        getLogger().info("KwadratoweGildie v3.0 z kolorami zostały włączone!");
    }

    @Override
    public void onDisable() {
        saveGuilds();
        getLogger().info("KwadratoweGildie zostały wyłączone.");
    }

    private void createFile() {
        file = new File(getDataFolder(), "gildie.yml");
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    private void loadGuilds() {
        if (!config.contains("guilds")) return;
        ConfigurationSection section = config.getConfigurationSection("guilds");
        for (String gName : section.getKeys(false)) {
            String tag = section.getString(gName + ".tag", gName);
            String leader = section.getString(gName + ".leader");
            String colorName = section.getString(gName + ".color", "GREEN");
            ChatColor color;
            try {
                color = ChatColor.valueOf(colorName);
            } catch (Exception e) {
                color = ChatColor.GREEN;
            }
            Location loc = section.getLocation(gName + ".center");
            List<String> membersList = section.getStringList(gName + ".members");

            Guild guild = new Guild(gName, tag, leader, color, loc);
            guild.members.clear();
            guild.members.addAll(membersList);

            guilds.put(gName.toLowerCase(), guild);
            for (String member : guild.members) {
                playerGuilds.put(member.toLowerCase(), gName.toLowerCase());
            }
        }
    }

    private void saveGuilds() {
        config.set("guilds", null);
        for (Map.Entry<String, Guild> entry : guilds.entrySet()) {
            String path = "guilds." + entry.getValue().name + ".";
            config.set(path + "tag", entry.getValue().tag);
            config.set(path + "leader", entry.getValue().leader);
            config.set(path + "color", entry.getValue().color.name());
            config.set(path + "center", entry.getValue().center);
            config.set(path + "members", new ArrayList<>(entry.getValue().members));
        }
        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tę komendę mogą wykonać tylko gracze!");
            return true;
        }

        Player player = (Player) sender;
        String pName = player.getName();
        String pLower = pName.toLowerCase();

        if (args.length == 0) {
            player.sendMessage(ChatColor.GOLD + "=== System Gildii (Kwadratowa Masakra) ===");
            player.sendMessage(ChatColor.YELLOW + "/g zaloz <nazwa> <tag>" + ChatColor.WHITE + " - Tworzy gildię (Koszt: " + DIAMOND_COST + " diamentów)");
            player.sendMessage(ChatColor.YELLOW + "/g zapros <gracz>" + ChatColor.WHITE + " - Zaprasza gracza do gildii");
            player.sendMessage(ChatColor.YELLOW + "/g dolacz <nazwa>" + ChatColor.WHITE + " - Dołącza do gildii");
            player.sendMessage(ChatColor.YELLOW + "/g wyrzuc <gracz>" + ChatColor.WHITE + " - Wyrzuca gracza z gildii");
            player.sendMessage(ChatColor.YELLOW + "/g usun" + ChatColor.WHITE + " - Usuwa Twoją gildię");
            player.sendMessage(ChatColor.YELLOW + "/g info" + ChatColor.WHITE + " - Informacje o Twojej gildii");
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("zaloz")) {
            if (args.length < 3) {
                player.sendMessage(ChatColor.RED + "Użycie: /g zaloz <nazwa> <tag>");
                return true;
            }
            if (playerGuilds.containsKey(pLower)) {
                player.sendMessage(ChatColor.RED + "Posiadasz już gildię!");
                return true;
            }

            String gName = args[1];
            String gTag = args[2];

            if (gTag.length() > 5) {
                player.sendMessage(ChatColor.RED + "Tag gildii może mieć maksymalnie 5 znaków!");
                return true;
            }

            if (guilds.containsKey(gName.toLowerCase())) {
                player.sendMessage(ChatColor.RED + "Gildia o takiej nazwie już istnieje!");
                return true;
            }

            if (!player.getInventory().contains(Material.DIAMOND, DIAMOND_COST)) {
                player.sendMessage(ChatColor.RED + "Potrzebujesz " + DIAMOND_COST + " diamentów!");
                return true;
            }

            Location loc = player.getLocation();
            for (Guild g : guilds.values()) {
                if (g.center.getWorld().equals(loc.getWorld())) {
                    if (g.center.distance(loc) < (PROTECTION_RADIUS * 2 + 10)) {
                        player.sendMessage(ChatColor.RED + "Zbyt blisko bazy innej gildii!");
                        return true;
                    }
                }
            }

            List<ChatColor> usedColors = new ArrayList<>();
            for (Guild g : guilds.values()) {
                usedColors.add(g.color);
            }
            ChatColor guildColor = ChatColor.GREEN;
            for (ChatColor c : availableColors) {
                if (!usedColors.contains(c)) {
                    guildColor = c;
                    break;
                }
            }

            player.getInventory().removeItem(new org.bukkit.inventory.ItemStack(Material.DIAMOND, DIAMOND_COST));

            Guild newGuild = new Guild(gName, gTag, pName, guildColor, loc);
            guilds.put(gName.toLowerCase(), newGuild);
            playerGuilds.put(pLower, gName.toLowerCase());
            saveGuilds();

            Bukkit.broadcastMessage(ChatColor.GOLD + "[Gildie] " + guildColor + "Gracz " + pName + " założył gildię " + gName + " [" + gTag + "]!");
            player.sendMessage(ChatColor.GREEN + "Gildia utworzona pomyślnie!");
            return true;
        }

        if (sub.equals("usun")) {
            if (!playerGuilds.containsKey(pLower)) {
                player.sendMessage(ChatColor.RED + "Nie należysz do żadnej gildii!");
                return true;
            }
            String gKey = playerGuilds.get(pLower);
            Guild g = guilds.get(gKey);

            if (!g.leader.equalsIgnoreCase(pName)) {
                player.sendMessage(ChatColor.RED + "Tylko lider może usunąć gildię!");
                return true;
            }

            for (String member : g.members) {
                playerGuilds.remove(member.toLowerCase());
            }
            guilds.remove(gKey);
            saveGuilds();

            Bukkit.broadcastMessage(ChatColor.GOLD + "[Gildie] " + ChatColor.YELLOW + "Gildia " + g.name + " została usunięta.");
            return true;
        }

        if (sub.equals("zapros")) {
            if (!playerGuilds.containsKey(pLower)) {
                player.sendMessage(ChatColor.RED + "Nie masz gildii!");
                return true;
            }
            String gKey = playerGuilds.get(pLower);
            Guild g = guilds.get(gKey);

            if (!g.leader.equalsIgnoreCase(pName)) {
                player.sendMessage(ChatColor.RED + "Tylko lider może zapraszać!");
                return true;
            }

            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "Użycie: /g zapros <gracz>");
                return true;
            }

            Player target = Bukkit.getPlayer(args[1]);
            if (target == null || !target.isOnline()) {
                player.sendMessage(ChatColor.RED + "Gracz jest niedostępny.");
                return true;
            }

            invites.put(target.getName().toLowerCase(), g.name);
            target.sendMessage(ChatColor.GOLD + "[Gildie] " + ChatColor.YELLOW + "Zaproszenie do gildii " + g.color + g.name);
            player.sendMessage(ChatColor.GREEN + "Wysłano zaproszenie.");
            return true;
        }

        if (sub.equals("dolacz")) {
            if (playerGuilds.containsKey(pLower)) {
                player.sendMessage(ChatColor.RED + "Już masz gildię!");
                return true;
            }
            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "Użycie: /g dolacz <nazwa>");
                return true;
            }

            String targetGuildName = args[1].toLowerCase();
            String invitedTo = invites.get(pLower);

            if (invitedTo == null || !invitedTo.equalsIgnoreCase(targetGuildName)) {
                player.sendMessage(ChatColor.RED + "Brak zaproszenia do tej gildii!");
                return true;
            }

            Guild g = guilds.get(targetGuildName);
            if (g == null) {
                player.sendMessage(ChatColor.RED + "Gildia już nie istnieje.");
                return true;
            }

            g.members.add(pName);
            playerGuilds.put(pLower, targetGuildName);
            invites.remove(pLower);
            saveGuilds();
            return true;
        }

        if (sub.equals("wyrzuc")) {
            if (!playerGuilds.containsKey(pLower)) return true;
            String gKey = playerGuilds.get(pLower);
            Guild g = guilds.get(gKey);

            if (!g.leader.equalsIgnoreCase(pName)) {
                player.sendMessage(ChatColor.RED + "Tylko lider może wyrzucać!");
                return true;
            }

            if (args.length < 2) return true;
            String targetName = args[1];

            if (!g.members.contains(targetName)) {
                player.sendMessage(ChatColor.RED + "Gracz nie jest w Twojej gildii.");
                return true;
            }

            g.members.remove(targetName);
            playerGuilds.remove(targetName.toLowerCase());
            saveGuilds();
            player.sendMessage(ChatColor.GREEN + "Wyrzucono gracza.");
            return true;
        }

        if (sub.equals("info")) {
            if (!playerGuilds.containsKey(pLower)) {
                player.sendMessage(ChatColor.RED + "Nie masz gildii!");
                return true;
            }
            Guild g = guilds.get(playerGuilds.get(pLower));
            player.sendMessage(ChatColor.GOLD + "=== Gildia: " + g.color + g.name + " [" + g.tag + "] ===");
            player.sendMessage(ChatColor.YELLOW + "Lider: " + ChatColor.WHITE + g.leader);
            player.sendMessage(ChatColor.YELLOW + "Członkowie: " + ChatColor.WHITE + String.join(", ", g.members));
            return true;
        }

        return true;
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String pLower = player.getName().toLowerCase();

        if (playerGuilds.containsKey(pLower)) {
            Guild g = guilds.get(playerGuilds.get(pLower));
            if (g != null) {
                event.setFormat(g.color + "[" + g.tag + "] " + ChatColor.AQUA + "%s" + ChatColor.WHITE + ": " + g.color + "%s");
            }
        } else {
            event.setFormat(ChatColor.GRAY + "[Brak] " + ChatColor.AQUA + "%s" + ChatColor.WHITE + ": " + ChatColor.WHITE + "%s");
        }
    }

    private Guild getGuildAt(Location loc) {
        for (Guild g : guilds.values()) {
            if (g.center.getWorld().equals(loc.getWorld())) {
                int dx = Math.abs(loc.getBlockX() - g.center.getBlockX());
                int dz = Math.abs(loc.getBlockZ() - g.center.getBlockZ());
                if (dx <= PROTECTION_RADIUS && dz <= PROTECTION_RADIUS) return g;
            }
        }
        return null;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        Guild g = getGuildAt(event.getBlock().getLocation());
        if (g != null) {
            String pLower = p.getName().toLowerCase();
            if (!playerGuilds.containsKey(pLower) || !playerGuilds.get(pLower).equals(g.name.toLowerCase())) {
                event.setCancelled(true);
                p.sendMessage(ChatColor.RED + "Teren gildii " + g.name + "!");
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player p = event.getPlayer();
        Guild g = getGuildAt(event.getBlock().getLocation());
        if (g != null) {
            String pLower = p.getName().toLowerCase();
            if (!playerGuilds.containsKey(pLower) || !playerGuilds.get(pLower).equals(g.name.toLowerCase())) {
                event.setCancelled(true);
                p.sendMessage(ChatColor.RED + "Teren gildii " + g.name + "!");
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        Material type = event.getClickedBlock().getType();
        if (type.name().contains("CHEST") || type.name().contains("DOOR") || type.name().contains("GATE") || type.name().contains("BUTTON")) {
            Guild g = getGuildAt(event.getClickedBlock().getLocation());
            if (g != null) {
                Player p = event.getPlayer();
                String pLower = p.getName().toLowerCase();
                if (!playerGuilds.containsKey(pLower) || !playerGuilds.get(pLower).equals(g.name.toLowerCase())) {
                    event.setCancelled(true);
                    p.sendMessage(ChatColor.RED + "Zabezpieczone przez gildię " + g.name + "!");
                }
            }
        }
    }
}
