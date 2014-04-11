package me.flexo.buildoffmanager;

import com.sk89q.worldedit.BlockVector;
import com.sk89q.worldguard.bukkit.WGBukkit;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.databases.ProtectionDatabaseException;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * @author Flexo013
 */
public class BuildOffManager extends JavaPlugin {

    private static final Logger log = Logger.getLogger("Minecraft");
    public ArrayList<String> BuildOffContestants = new ArrayList<>();
    public boolean JoinableBuildOff = false;
    public boolean RunningBuildOff = false;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();

        BuildOffContestants = (ArrayList) getConfig().getStringList("contestants");
        JoinableBuildOff = getConfig().getBoolean("buildoff.joinable");
        RunningBuildOff = getConfig().getBoolean("buildoff.running");
        log.info("[BuildOffManager] Build Off configuration loaded.");
        PluginManager pm = getServer().getPluginManager();
    }

    @Override
    public void onDisable() {
        getConfig().set("contestants", BuildOffContestants);
        getConfig().set("buildoff.joinable", JoinableBuildOff);
        getConfig().set("buildoff.running", RunningBuildOff);
        saveConfig();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
        // Allows all contestants to build in their own plots.
        if (cmd.getName().equalsIgnoreCase("startbuildoff")) {
            if (RunningBuildOff) {
                sender.sendMessage(ChatColor.RED + "The Build Off is already running.");
            } else {
                try {
                    if (!JoinableBuildOff) {
                        JoinableBuildOff = true;
                        sender.sendMessage(ChatColor.GREEN + "People can now join the Build Off using " + ChatColor.BLUE + "/join" + ChatColor.GREEN + ".");
                    }
                    RunningBuildOff = true;
                    sender.sendMessage(ChatColor.GREEN + "You have started the Build Off.");
                    RegionManager rgm = WGBukkit.getRegionManager(getServer().getWorld("Yavana2"));
                    ProtectedRegion rgContest = rgm.getRegion("contestcomplete");
                    rgContest.setPriority(0);
                    rgm.save();
                    getServer().broadcastMessage(ChatColor.GOLD + "The Build Off has started! You will have 24 hours to complete your build. The theme is: " + ChatColor.BLUE + ChatColor.BOLD + getConfig().getString("theme.line1") + " " + getConfig().getString("theme.line2"));
                    updateThemeSign();
                    //Add start of broadcasting here
                } catch (ProtectionDatabaseException ex) {
                    Logger.getLogger(BuildOffManager.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            return true;
        }

        // Opens a new Build Off so people are allowed to do /join
        if (cmd.getName().equalsIgnoreCase("openbuildoff")) {
            if (JoinableBuildOff) {
                sender.sendMessage(ChatColor.RED + "The Build Off is already opened. People can join using " + ChatColor.BLUE + "/join" + ChatColor.RED + ".");
            } else {
                JoinableBuildOff = true;
                sender.sendMessage(ChatColor.GREEN + "People can now join the Build Off using " + ChatColor.BLUE + "/join" + ChatColor.GREEN + ".");
                getServer().broadcastMessage(ChatColor.GOLD + "The Build Off has been opened. Do " + ChatColor.BLUE + "/join" + ChatColor.GOLD + " to enroll yourself!");
            }
            return true;
        }

        // Ends the current Build Off.
        if (cmd.getName().equalsIgnoreCase("endbuildoff")) {
            if (RunningBuildOff) {
                try {
                    JoinableBuildOff = false;
                    RunningBuildOff = false;
                    RegionManager rgm = WGBukkit.getRegionManager(getServer().getWorld("Yavana2"));
                    ProtectedRegion rgContest = rgm.getRegion("contestcomplete");
                    rgContest.setPriority(2);
                    rgm.save();
                    getServer().broadcastMessage(ChatColor.GOLD + "The Build Off has ended! Judging will commence soon. You can watch the judging live at: " + ChatColor.BLUE + getConfig().getString("streamlink"));
                } catch (ProtectionDatabaseException ex) {
                    Logger.getLogger(BuildOffManager.class.getName()).log(Level.SEVERE, null, ex);
                }
            } else {
                sender.sendMessage(ChatColor.RED + "There is no Build Off that you can end.");
            }
            return true;
        }

        // Resets the Build Off plots and clear the contestant list
        if (cmd.getName().equalsIgnoreCase("resetbuildoff")) {
            if (RunningBuildOff || JoinableBuildOff) {
                sender.sendMessage(ChatColor.RED + "You can not reset the Build Off area right now.");
            } else {
                sender.sendMessage(ChatColor.GREEN + "You just reset the Build Off area. You should be proud of yourself!");
                resetThemeSign();
                for (String playerName : BuildOffContestants) {
                    int plotNumber;
                    plotNumber = BuildOffContestants.indexOf(playerName);
                    resetPlot(plotNumber);
                    sender.sendMessage(ChatColor.GREEN + "Succesfully reset plot " + Integer.toString(plotNumber + 1));
                }
                resetBoard();
                BuildOffContestants.clear();
            }
            return true;
        }

        //Resets one Build Off plot specified by int number
        if (cmd.getName().equalsIgnoreCase("resetplot")) {
            if (!(args.length == 1)) {
                return false;
            } else {
                int number = Integer.parseInt(args[0]) - 1;
                resetPlot(number);
                sender.sendMessage(ChatColor.GREEN + "Succesfully regenerated plot " + Integer.toString(number + 1));
            }
            return true;
        }

        // Lists all players who enrolled for the Build Off
        if (cmd.getName().equalsIgnoreCase("listplayers")) {
            if (BuildOffContestants.isEmpty()) {
                sender.sendMessage(ChatColor.GOLD + "Currently nobody is competing in the Build Off.");
            } else {
                String playerString;
                boolean playerOnline;
                playerString = ChatColor.GOLD + "List of players that currently compete in the Build Off: " + ChatColor.YELLOW;
                for (String playerName : BuildOffContestants) {
                    playerOnline = false;
                    for (Player player : sender.getServer().getOnlinePlayers()) {
                        if (player.getPlayerListName().equals(playerName)) {
                            playerString = playerString + " " + ChatColor.GREEN + playerName;
                            playerOnline = true;
                        }
                    }
                    if (!playerOnline) {
                        playerString = playerString + " " + ChatColor.YELLOW + playerName;
                    }
                }
                sender.sendMessage(playerString);
            }
            return true;
        }

        // Allows a player to join the current Build Off
        if (cmd.getName().equalsIgnoreCase("join")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("The /join command can only be used by players.");
            } else {
                if (JoinableBuildOff) {
                    if ((int) BuildOffContestants.size() < getConfig().getInt("buildoff.maxcontestants")) {
                        joinBuildOff(sender);
                    } else {
                        sender.sendMessage(ChatColor.RED + "The Build Off Area is currently full. Contact an operator to join the Build Off. We are sorry for the inconvenience.");
                    }
                } else {
                    sender.sendMessage(ChatColor.RED + "You cannot join a Build Off at this time.");
                }
            }
            return true;
        }

        // Not supported at this time
        if (cmd.getName().equalsIgnoreCase("leave")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("The /leave command can only be used by players.");
            } else {
                sender.sendMessage(ChatColor.RED + "Currently you cannot leave the Build Off.");
            }
            return true;
        }

        // Teleports a player to their plot if they have one
        if (cmd.getName().equalsIgnoreCase("tpplot")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("The /tpplot command can only be used by players.");
            } else {
                Player player = (Player) sender;
                if (BuildOffContestants.contains(player.getName())) {
                    int plotNumber;
                    plotNumber = BuildOffContestants.indexOf(player.getName());
                    Location teleLoc;
                    int pathWidth = getConfig().getInt("layout.pathwidth");
                    int plotWidth = getConfig().getInt("layout.plotwidth");
                    int plotsPerRow = getConfig().getInt("layout.plotsperrow");
                    teleLoc = new Location(getServer().getWorld("Yavana2"), 1715.5 - ((plotNumber % plotsPerRow) * (plotWidth + pathWidth)), 64, 1501.5 + (((int) (plotNumber / plotsPerRow)) * (plotWidth + pathWidth)));
                    player.teleport(teleLoc);
                } else {
                    sender.sendMessage(ChatColor.RED + "You are not enrolled for the Build Off. So you cannot be teleported to your plot.");
                }
            }
            return true;
        }

        //Tells a player the theme
        if (cmd.getName().equalsIgnoreCase("theme")) {
            if (RunningBuildOff) {
                sender.sendMessage(ChatColor.GOLD + "The theme is: " + ChatColor.BLUE + getConfig().getString("theme.line1") + " " + getConfig().getString("theme.line2"));
            } else {
                sender.sendMessage(ChatColor.RED + "You can not see the theme, if there is no Build Off running.");
            }
            return true;
        }

        //Changes the Build Off Theme in the config.yml
        if (cmd.getName().equalsIgnoreCase("settheme")) {
            if (!(args.length == 2)) {
                System.out.println("meow");
                return false;
            } else {
                if (!args[0].equals("1") || !args[0].equals("2")) {
                    return false;
                } else {
                    if (args[0].equals("1")) {
                        getConfig().set("theme.line1", args[1]);
                        sender.sendMessage(ChatColor.GREEN + "You succesfully changed Themeline1 to: " + ChatColor.BLUE + getConfig().getString("theme.line1"));
                    } else if (args[0].equals("2")) {
                        getConfig().set("theme.line2", args[1]);
                        sender.sendMessage(ChatColor.GREEN + "You succesfully changed Themeline2 to: " + ChatColor.BLUE + getConfig().getString("theme.line2"));
                    }
                    saveConfig();
                }
            }
            return true;
        }

        //Changes the Streamlink in the config.yml
        if (cmd.getName().equalsIgnoreCase("setstreamlink")) {
            if (!(args.length == 1)) {
                return false;
            } else {
                getConfig().set("streamlink", args[0]);
                saveConfig();
                sender.sendMessage(ChatColor.GREEN + "You succesfully changed the stream link to: " + ChatColor.BLUE + getConfig().getString("streamlink"));
            }
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("bominit")) {
            int max = getConfig().getInt("buildoff.maxcontestants");
            for (int i = 0; i < max; i++) {
                initializePlots(i);
            }
            sender.sendMessage("Done YaY");
            return true;
        }
        return false;
    }

    private void joinBuildOff(CommandSender sender) {
        if (BuildOffContestants.contains(sender.getName())) {
            sender.sendMessage(ChatColor.RED + "You are already enrolled for the Build Off.");
        } else {
            BuildOffContestants.add(sender.getName());
            if (RunningBuildOff) {
                sender.sendMessage(ChatColor.GREEN + "You have joined the Build Off! You can go to your plot using " + ChatColor.BLUE + "/tpplot" + ChatColor.GREEN + ". The theme is: " + ChatColor.BLUE + getConfig().getString("theme.line1") + " " + getConfig().getString("theme.line2"));
            } else {
                sender.sendMessage(ChatColor.GREEN + "You have joined the Build Off! You can go to your plot using " + ChatColor.BLUE + "/tpplot" + ChatColor.GREEN + ". Do " + ChatColor.BLUE + "/theme" + ChatColor.GREEN + " to find out what the Theme is after the Build Off started.");
            }
            preparePlotSign(sender);
            updateBoard(sender);
            updateRegions(sender.getName());
        }
    }

    private void preparePlotSign(CommandSender sender) {
        int plotNumber;
        plotNumber = BuildOffContestants.indexOf(sender.getName());
        Location boardSign;
        int pathWidth = getConfig().getInt("layout.pathwidth");
        int plotWidth = getConfig().getInt("layout.plotwidth");
        int plotsPerRow = getConfig().getInt("layout.plotsperrow");
        boardSign = new Location(getServer().getWorld("Yavana2"), 1715 - ((plotNumber % plotsPerRow) * (plotWidth + pathWidth)), 65, 1504 + (((int) (plotNumber / plotsPerRow)) * (plotWidth + pathWidth)));
        boardSign.getBlock().setType(Material.SIGN_POST);
        boardSign.getBlock().setData((byte) 10);
        Sign sign;
        sign = (Sign) boardSign.getBlock().getState();
        sign.setLine(0, Integer.toString(plotNumber + 1));
        sign.setLine(2, sender.getName());
        sign.update();
    }

    private void updateBoard(CommandSender sender) {
        int plotNumber;
        int plotsPerRow = getConfig().getInt("layout.plotsperrow");
        plotNumber = BuildOffContestants.indexOf(sender.getName());
        Location boardSign;
        boardSign = new Location(getServer().getWorld("Yavana2"), 1654 - ((plotNumber % plotsPerRow) * 1), 70 + (((int) (plotNumber / plotsPerRow)) * 1), 1446);
        boardSign.getBlock().setType(Material.WALL_SIGN);
        boardSign.getBlock().setData((byte) 2);
        Sign sign;
        sign = (Sign) boardSign.getBlock().getState();
        sign.setLine(0, Integer.toString(plotNumber + 1));
        sign.setLine(2, sender.getName());
        sign.update();
    }

    private void updateThemeSign() {
        Location loc;
        loc = new Location(getServer().getWorld("Yavana2"), 1609, 65, 1447);
        loc.getBlock().setType(Material.WALL_SIGN);
        loc.getBlock().setData((byte) 2);
        String subString1 = getConfig().getString("theme.line1");
        String subString2 = getConfig().getString("theme.line2");
        Sign sign;
        sign = (Sign) loc.getBlock().getState();
        sign.setLine(0, "=-=-=-=-=-=-=-=");
        sign.setLine(1, ChatColor.DARK_AQUA + "" + ChatColor.BOLD + subString1);
        sign.setLine(2, ChatColor.DARK_AQUA + "" + ChatColor.BOLD + subString2);
        sign.setLine(3, "=-=-=-=-=-=-=-=");
        sign.update();
    }

    private void updateRegions(String name) {
        try {
            int plotNumber;
            plotNumber = BuildOffContestants.indexOf(name);
            RegionManager rgm = WGBukkit.getRegionManager(getServer().getWorld("Yavana2"));
            ProtectedRegion rgSmall = rgm.getRegion("plotbig" + Integer.toString(plotNumber));
            ProtectedRegion rgBig = rgm.getRegion("plotsmall" + Integer.toString(plotNumber));
            DefaultDomain dd = new DefaultDomain();
            dd.addPlayer(name.toLowerCase());
            rgSmall.setMembers(dd);
            rgBig.setMembers(dd);
            rgm.addRegion(rgBig);
            rgm.addRegion(rgSmall);
            rgm.save();
        } catch (ProtectionDatabaseException ex) {
            Logger.getLogger(BuildOffManager.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void resetBoard() {
        Location l1, l2;
        l1 = new Location(getServer().getWorld("Yavana2"), 1654, 70, 1446);
        l2 = new Location(getServer().getWorld("Yavana2"), 1649, 75, 1446);
        setBlocks(l1, l2, Material.AIR);
    }

    private void resetThemeSign() {
        Location loc;
        loc = new Location(getServer().getWorld("Yavana2"), 1609, 65, 1447);
        loc.getBlock().setType(Material.WALL_SIGN);
        loc.getBlock().setData((byte) 2);
        Sign sign;
        sign = (Sign) loc.getBlock().getState();
        sign.setLine(0, "=-=-=-=-=-=-=-=");
        sign.setLine(1, ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Secret till");
        sign.setLine(2, ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "the start");
        sign.setLine(3, "=-=-=-=-=-=-=-=");
        sign.update();
    }

    private void resetPlot(int number) {
        try {
            int pathWidth = getConfig().getInt("layout.pathwidth");
            int plotWidth = getConfig().getInt("layout.plotwidth");
            int plotsPerRow = getConfig().getInt("layout.plotsperrow");
            final int deltaX = ((int) (number % plotsPerRow)) * (plotWidth + pathWidth);
            final int deltaZ = ((int) (number / plotsPerRow)) * (plotWidth + pathWidth);
            Location l1 = new Location(getServer().getWorld("Yavana2"), 1685 - (deltaX), 59, 1534 + deltaZ);
            Location l2 = new Location(getServer().getWorld("Yavana2"), 1714 - (deltaX), 1, 1505 + deltaZ);
            setBlocks(l1, l2, Material.STONE);
            Location l3 = new Location(getServer().getWorld("Yavana2"), 1685 - (deltaX), 62, 1534 + deltaZ);
            Location l4 = new Location(getServer().getWorld("Yavana2"), 1714 - (deltaX), 60, 1505 + deltaZ);
            setBlocks(l3, l4, Material.DIRT);
            Location l5 = new Location(getServer().getWorld("Yavana2"), 1685 - (deltaX), 63, 1534 + deltaZ);
            Location l6 = new Location(getServer().getWorld("Yavana2"), 1714 - (deltaX), 63, 1505 + deltaZ);
            setBlocks(l5, l6, Material.GRASS);
            Location l7 = new Location(getServer().getWorld("Yavana2"), 1684 - (deltaX), 64, 1535 + deltaZ);
            Location l8 = new Location(getServer().getWorld("Yavana2"), 1715 - (deltaX), 64, 1504 + deltaZ);
            setBlocks(l7, l8, Material.STEP);
            Location l9 = new Location(getServer().getWorld("Yavana2"), 1685 - (deltaX), 255, 1534 + deltaZ);
            Location l10 = new Location(getServer().getWorld("Yavana2"), 1714 - (deltaX), 64, 1505 + deltaZ);
            setBlocks(l9, l10, Material.AIR);
            Location l11 = new Location(getServer().getWorld("Yavana2"), 1715 - (deltaX), 64, 1504 + deltaZ);
            getServer().getWorld("Yavana2").getBlockAt(l11).setType(Material.GLOWSTONE);
            Location plotSign;
            plotSign = new Location(getServer().getWorld("Yavana2"), 1715 - (deltaX), 65, 1504 + deltaZ);
            plotSign.getBlock().setType(Material.SIGN_POST);
            plotSign.getBlock().setData((byte) 10);
            Sign sign;
            sign = (Sign) plotSign.getBlock().getState();
            sign.setLine(2, "");
            sign.update();
            
            //Make sign say [RESET]
            Location boardSign;
            boardSign = new Location(getServer().getWorld("Yavana2"), 1654 - ((number % plotsPerRow) * 1), 70 + (((int) (number / plotsPerRow)) * 1), 1446);
            boardSign.getBlock().setType(Material.WALL_SIGN);
            boardSign.getBlock().setData((byte) 2);
            Sign sign2;
            sign2 = (Sign) boardSign.getBlock().getState();
            sign2.setLine(1, "[RESET]");
            sign2.update();
            
            //Reset the regions
            RegionManager rgm = WGBukkit.getRegionManager(getServer().getWorld("Yavana2"));
            BlockVector bv1 = new BlockVector(l2.getBlockX(), l2.getBlockY(), l2.getBlockZ());
            BlockVector bv2 = new BlockVector(l9.getBlockX(), l9.getBlockY(), l9.getBlockZ());
            ProtectedCuboidRegion pcr1 = new ProtectedCuboidRegion(("plotbig" + Integer.toString(number)), bv1, bv2);
            BlockVector bv3 = new BlockVector(l7.getBlockX(), l7.getBlockY(), l7.getBlockZ());
            BlockVector bv4 = new BlockVector(l8.getBlockX(), l8.getBlockY(), l8.getBlockZ());
            ProtectedCuboidRegion pcr2 = new ProtectedCuboidRegion(("plotsmall" + Integer.toString(number)), bv3, bv4);
//        DefaultDomain emptydd = new DefaultDomain();
//        pcr1.setMembers(emptydd);
//        pcr2.setMembers(emptydd);
            rgm.addRegion(pcr1);
            rgm.addRegion(pcr2);
            rgm.save(); 
        } catch (ProtectionDatabaseException ex) {
            Logger.getLogger(BuildOffManager.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void setBlocks(Location loc1, Location loc2, Material mat) {
        this.setBlocks(loc1, loc2, mat, (byte) 0);
    }

    private void setBlocks(Location loc1, Location loc2, Material mat, byte data) {
        Cuboid cub;
        cub = new Cuboid(loc1, loc2);
        for (Block b : cub) {
            b.setType(mat);
            b.setData(data);
        }
    }

    private void initializePlots(int number) {
        try {
            int pathWidth = getConfig().getInt("layout.pathwidth");
            int plotWidth = getConfig().getInt("layout.plotwidth");
            int plotsPerRow = getConfig().getInt("layout.plotsperrow");
            final int deltaX = ((int) (number % plotsPerRow)) * (plotWidth + pathWidth);
            final int deltaZ = ((int) (number / plotsPerRow)) * (plotWidth + pathWidth);
            Location l1 = new Location(getServer().getWorld("Yavana2"), 1685 - (deltaX), 59, 1534 + deltaZ);
            Location l2 = new Location(getServer().getWorld("Yavana2"), 1714 - (deltaX), 1, 1505 + deltaZ);
            setBlocks(l1, l2, Material.STONE);
            Location l3 = new Location(getServer().getWorld("Yavana2"), 1685 - (deltaX), 62, 1534 + deltaZ);
            Location l4 = new Location(getServer().getWorld("Yavana2"), 1714 - (deltaX), 60, 1505 + deltaZ);
            setBlocks(l3, l4, Material.DIRT);
            Location l5 = new Location(getServer().getWorld("Yavana2"), 1685 - (deltaX), 63, 1534 + deltaZ);
            Location l6 = new Location(getServer().getWorld("Yavana2"), 1714 - (deltaX), 63, 1505 + deltaZ);
            setBlocks(l5, l6, Material.GRASS);
            Location l7 = new Location(getServer().getWorld("Yavana2"), 1684 - (deltaX), 64, 1535 + deltaZ);
            Location l8 = new Location(getServer().getWorld("Yavana2"), 1715 - (deltaX), 64, 1504 + deltaZ);
            setBlocks(l7, l8, Material.STEP);
            Location l9 = new Location(getServer().getWorld("Yavana2"), 1685 - (deltaX), 255, 1534 + deltaZ);
            Location l10 = new Location(getServer().getWorld("Yavana2"), 1714 - (deltaX), 64, 1505 + deltaZ);
            setBlocks(l9, l10, Material.AIR);
            Location l11 = new Location(getServer().getWorld("Yavana2"), 1715 - (deltaX), 64, 1504 + deltaZ);
            getServer().getWorld("Yavana2").getBlockAt(l11).setType(Material.GLOWSTONE);
            Location plotSign;
            plotSign = new Location(getServer().getWorld("Yavana2"), 1715 - (deltaX), 65, 1504 + deltaZ);
            plotSign.getBlock().setType(Material.SIGN_POST);
            plotSign.getBlock().setData((byte) 10);
            Sign sign;
            sign = (Sign) plotSign.getBlock().getState();
            sign.setLine(0, Integer.toString(number + 1));
            sign.setLine(2, "");
            sign.update();
            
            RegionManager rgm = WGBukkit.getRegionManager(getServer().getWorld("Yavana2"));
            BlockVector bv1 = new BlockVector(l2.getBlockX(), l2.getBlockY(), l2.getBlockZ());
            BlockVector bv2 = new BlockVector(l9.getBlockX(), l9.getBlockY(), l9.getBlockZ());
            ProtectedCuboidRegion pcr1 = new ProtectedCuboidRegion(("plotbig" + Integer.toString(number)), bv1, bv2);
            BlockVector bv3 = new BlockVector(l7.getBlockX(), l7.getBlockY(), l7.getBlockZ());
            BlockVector bv4 = new BlockVector(l8.getBlockX(), l8.getBlockY(), l8.getBlockZ());
            ProtectedCuboidRegion pcr2 = new ProtectedCuboidRegion(("plotsmall" + Integer.toString(number)), bv3, bv4);
            pcr1.setPriority(1);
            pcr2.setPriority(1);
//        DefaultDomain emptydd = new DefaultDomain();
//        pcr1.setMembers(emptydd);
//        pcr2.setMembers(emptydd);
            rgm.addRegion(pcr1);
            rgm.addRegion(pcr2);
            rgm.removeRegion(("plot" + Integer.toString(number)) + "small");
            rgm.removeRegion(("plot" + Integer.toString(number)) + "big");
            rgm.save();
        } catch (ProtectionDatabaseException ex) {
            Logger.getLogger(BuildOffManager.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
