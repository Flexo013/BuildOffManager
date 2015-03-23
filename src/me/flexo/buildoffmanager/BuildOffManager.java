package me.flexo.buildoffmanager;

import com.sk89q.worldedit.BlockVector;
import com.sk89q.worldedit.BlockVector2D;
import com.sk89q.worldguard.bukkit.WGBukkit;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.flags.DefaultFlag;
import com.sk89q.worldguard.protection.flags.StateFlag.State;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.managers.storage.StorageException;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import java.util.ArrayList;
import java.util.List;
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
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * @author Flexo013
 */
public class BuildOffManager extends JavaPlugin implements Listener {

    private static final Logger log = Logger.getLogger("Minecraft");
    public ArrayList<String> BuildOffContestants = new ArrayList<>();
    public boolean JoinableBuildOff = false;
    public boolean RunningBuildOff = false;
    public boolean AfterBuildOff = false;
    public String PreFix = (ChatColor.DARK_BLUE + "[" + ChatColor.BLUE + "BuildOff" + ChatColor.DARK_BLUE + "] " + ChatColor.RESET);
    public String BroadcastPreFix = (ChatColor.GOLD + "|" + ChatColor.DARK_RED + "BuildOff" + ChatColor.GOLD + "| " + ChatColor.DARK_AQUA);

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        BuildOffContestants = (ArrayList) getConfig().getStringList("contestants");
        fillContestantsList();
        JoinableBuildOff = getConfig().getBoolean("buildoff.joinable");
        RunningBuildOff = getConfig().getBoolean("buildoff.running");
        AfterBuildOff = getConfig().getBoolean("buildoff.after");
        log.info("[BuildOffManager] Build Off configuration loaded.");
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        saveDefaultConfig();
        reloadConfig();
        getConfig().set("contestants", BuildOffContestants);
        getConfig().set("buildoff.joinable", JoinableBuildOff);
        getConfig().set("buildoff.running", RunningBuildOff);
        getConfig().set("buildoff.after", AfterBuildOff);
        saveConfig();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
        // Allows all contestants to build in their own plots.
        if (cmd.getName().equalsIgnoreCase("startbuildoff")) {
            return startBuildOff(sender);
        }
        if (cmd.getName().equalsIgnoreCase("startbo")) {
            return startBuildOff(sender);
        }

        // Opens a new Build Off so people are allowed to do /join
        if (cmd.getName().equalsIgnoreCase("openbuildoff")) {
            return openBuildOff(sender);
        }
        if (cmd.getName().equalsIgnoreCase("openbo")) {
            return openBuildOff(sender);
        }

        // Ends the current Build Off.
        if (cmd.getName().equalsIgnoreCase("endbuildoff")) {
            return endBuildOff(sender);
        }
        if (cmd.getName().equalsIgnoreCase("endbo")) {
            return endBuildOff(sender);
        }

        // Resets the Build Off plots and clear the contestant list
        if (cmd.getName().equalsIgnoreCase("resetbuildoff")) {
            return resetBuildOff(sender);
        }
        if (cmd.getName().equalsIgnoreCase("resetbo")) {
            return resetBuildOff(sender);
        }

        //Resets one Build Off plot specified by int number
        if (cmd.getName().equalsIgnoreCase("resetplot")) {
            if (!(args.length == 1)) {
                return false;
            } else {
                int number = Integer.parseInt(args[0]) - 1;
                resetPlot(number);
                sender.sendMessage(PreFix + ChatColor.GREEN + "Succesfully regenerated plot " + Integer.toString(number + 1));
            }
            return true;
        }

        // Lists all players who enrolled for the Build Off
        if (cmd.getName().equalsIgnoreCase("listplayers")) {
            if (BuildOffContestants.isEmpty()) {
                sender.sendMessage(PreFix + ChatColor.GOLD + "Currently nobody is competing in the Build Off.");
            } else {
                String playerString;
                boolean playerOnline;
                playerString = ChatColor.GOLD + "List of players that currently compete in the Build Off: " + ChatColor.YELLOW;
                for (String playerName : BuildOffContestants) {
                    if (!playerName.equals("")) {
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
                if (AfterBuildOff) {
                    sender.sendMessage(PreFix + ChatColor.RED + "The Build Off has ended. You cannot join anymore.");
                } else if (JoinableBuildOff) {
                    if (BuildOffContestants.contains(sender.getName())) {
                        sender.sendMessage(PreFix + ChatColor.RED + "You are already enrolled for the Build Off.");
                    } else if (isFullBuildOff()) {
                        sender.sendMessage(PreFix + ChatColor.RED + "The Build Off Area is currently full. Contact an operator to join the Build Off. We are sorry for the inconvenience.");
                    } else {
                        joinBuildOff(sender);
                    }
                } else {
                    sender.sendMessage(PreFix + ChatColor.RED + "The enrollments for the Build Off have not opened yet.");
                }
            }
            return true;
        }

        // Allows a player to join the current Build Off
        if (cmd.getName().equalsIgnoreCase("leave")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("The /leave command can only be used by players.");
            } else {
                if (BuildOffContestants.contains(sender.getName())) {
                    if (!RunningBuildOff && !AfterBuildOff) {
                        leaveBuildOff(sender);
                    } else {
                        sender.sendMessage(PreFix + ChatColor.RED + "You can only leave the Build Off before it starts.");
                    }
                } else {
                    sender.sendMessage(PreFix + ChatColor.RED + "You are not enrolled for the Build Off, so you cannot leave the Build Off.");
                }
            }
            return true;
        }

        // Teleports a player to a plot
        if (cmd.getName().equalsIgnoreCase("tpplot")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("The /tpplot command can only be used by players.");
            } else {
                Player player = (Player) sender;
                if (args.length == 0 || args[0].equals(player.getName())) {
                    if (BuildOffContestants.contains(player.getName())) {
                        tpToPlot(player.getName(), player);
                    } else {
                        sender.sendMessage(PreFix + ChatColor.RED + "You are not enrolled for the Build Off. So you cannot be teleported to your plot.");
                    }
                    return true;
                }
                if (args.length == 1) {
                    String targetPlotOwner = args[0];
                    if (BuildOffContestants.contains(targetPlotOwner)) {
                        tpToPlot(targetPlotOwner, player);
                    } else {
                        sender.sendMessage(PreFix + ChatColor.DARK_RED + targetPlotOwner + ChatColor.RED + " is not enrolled for the Build Off. So you cannot be teleported to their plot.");
                    }
                    return true;
                } else {
                    return false;
                }
            }
            return true;
        }

        //Tells a player the theme
        if (cmd.getName().equalsIgnoreCase("theme")) {
            if (RunningBuildOff) {
                sender.sendMessage(PreFix + ChatColor.GOLD + "The theme is: " + ChatColor.BLUE + getConfig().getString("theme"));
            } else if (AfterBuildOff) {
                sender.sendMessage(PreFix + ChatColor.GOLD + "The Build Off has ended. The theme was: " + ChatColor.BLUE + getConfig().getString("theme"));
            } else {
                sender.sendMessage(PreFix + ChatColor.RED + "You can not see the theme, since the Build Off hasn't started yet.");
            }
            return true;
        }

        //Changes the Build Off Theme in the config.yml
        if (cmd.getName().equalsIgnoreCase("settheme")) {
            if (!(args.length == 1)) {
                return false;
            } else {
                getConfig().set("theme", args[0]);
                saveConfig();
                sender.sendMessage(PreFix + ChatColor.GREEN + "You succesfully changed the theme to: " + ChatColor.BLUE + getConfig().getString("theme"));
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
                sender.sendMessage(PreFix + ChatColor.GREEN + "You succesfully changed the stream link to: " + ChatColor.BLUE + getConfig().getString("streamlink"));
            }
            return true;
        }

        //Initializes the Build Off plots
        if (cmd.getName().equalsIgnoreCase("bominit")) {
            int max = getConfig().getInt("buildoff.maxcontestants");
            for (int i = 0; i < max; i++) {
                initializePlots(i);
            }
            createCompleteRegion();
            sender.sendMessage(PreFix + ChatColor.GREEN + "Initializing Build Off complete!");
            return true;
        }

        //Reloads the config
        if (cmd.getName().equalsIgnoreCase("reloadbom")) {
            reloadConfig();
            sender.sendMessage(PreFix + ChatColor.GREEN + "You have successfully reloaded the BuildOffManager config.");
            return true;
        }
        return false;
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Player p = event.getPlayer();
            //start cuboid calc
            int maxPlotNumber = getConfig().getInt("buildoff.maxcontestants") - 1;
            int plotsPerRow = getConfig().getInt("layout.plotsperrow");
            String worldName = getConfig().getString("boardstartblock.world");
            int boardStartX = getConfig().getInt("boardstartblock.x");
            int boardStartY = getConfig().getInt("boardstartblock.y");
            int boardStartZ = getConfig().getInt("boardstartblock.z");
            Location l1, l2;
            l1 = new Location(getServer().getWorld(worldName), boardStartX, boardStartY, boardStartZ);
            l2 = new Location(getServer().getWorld(worldName), boardStartX - (plotsPerRow - 1), boardStartY + ((maxPlotNumber / plotsPerRow) * 1), boardStartZ);
            //end cuboid calc
            Cuboid signs = new Cuboid(l1, l2);
            if (signs.getBlocks().contains(event.getClickedBlock()) && (event.getClickedBlock().getType() == Material.WALL_SIGN)) {
                Sign sign = (Sign) event.getClickedBlock().getState();
                String line = sign.getLine(0);
                int plotNumber = Integer.parseInt(line.substring(5, line.length() - 3));
                tpToPlot(plotNumber - 1, p);
            }
        }

    }

    private void tpToPlot(String targetPlotOwner, Player player) {
        int plotNumber;
        plotNumber = BuildOffContestants.indexOf(targetPlotOwner);
        tpToPlot(plotNumber, player);
    }

    private void tpToPlot(int plotNumber, Player player) {
        Location teleLoc;
        int pathWidth = getConfig().getInt("layout.pathwidth");
        int plotWidth = getConfig().getInt("layout.plotwidth");
        int plotsPerRow = getConfig().getInt("layout.plotsperrow");
        String worldName = getConfig().getString("startblock.world");
        int startX = getConfig().getInt("startblock.x");
        int startY = getConfig().getInt("startblock.y");
        int startZ = getConfig().getInt("startblock.z");
        teleLoc = new Location(getServer().getWorld(worldName), (startX + 0.5) - ((plotNumber % plotsPerRow) * (plotWidth + pathWidth)), startY, (startZ - 2.5) + ((plotNumber / plotsPerRow) * (plotWidth + pathWidth)));
        player.teleport(teleLoc);
    }

    private boolean startBuildOff(CommandSender sender) {
        if (RunningBuildOff) {
            sender.sendMessage(PreFix + ChatColor.RED + "The Build Off is already running.");
        } else {
            try {
                if (!JoinableBuildOff) {
                    JoinableBuildOff = true;
                    sender.sendMessage(PreFix + ChatColor.GREEN + "People can now join the Build Off using " + ChatColor.BLUE + "/join" + ChatColor.GREEN + ".");
                }
                RunningBuildOff = true;
                sender.sendMessage(PreFix + ChatColor.GREEN + "You have started the Build Off.");
                String worldName = getConfig().getString("startblock.world");
                RegionManager rgm = WGBukkit.getRegionManager(getServer().getWorld(worldName));
                ProtectedRegion rgContest = rgm.getRegion("contestcomplete");
                rgContest.setPriority(0);
                rgm.save();
                getServer().broadcastMessage(BroadcastPreFix + "The Build Off has started! You will have 24 hours to complete your build. The theme is: " + ChatColor.BLUE + ChatColor.BOLD + getConfig().getString("theme"));
                updateThemeSign();
                //Add start of broadcasting here
            } catch (StorageException ex) {
                Logger.getLogger(BuildOffManager.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return true;
    }

    private boolean resetBuildOff(CommandSender sender) {
        if (RunningBuildOff || JoinableBuildOff) {
            sender.sendMessage(PreFix + ChatColor.RED + "You can not reset the Build Off area right now.");
        } else {
            sender.sendMessage(PreFix + ChatColor.GREEN + "You just reset the Build Off area. You should be proud of yourself!");
            AfterBuildOff = false;
            resetThemeSign();
            for (String playerName : BuildOffContestants) {
                if (!playerName.equals("")) {
                    int plotNumber;
                    plotNumber = BuildOffContestants.indexOf(playerName);
                    resetPlot(plotNumber);
                    sender.sendMessage(PreFix + ChatColor.GREEN + "Succesfully reset plot " + Integer.toString(plotNumber + 1));
                }
            }
            resetBoard();
            BuildOffContestants.clear();
            fillContestantsList();
        }
        return true;
    }

    private boolean openBuildOff(CommandSender sender) {
        if (JoinableBuildOff) {
            sender.sendMessage(PreFix + ChatColor.RED + "The Build Off is already opened. People can join using " + ChatColor.BLUE + "/join" + ChatColor.RED + ".");
        } else {
            JoinableBuildOff = true;
            sender.sendMessage(PreFix + ChatColor.GREEN + "People can now join the Build Off using " + ChatColor.BLUE + "/join" + ChatColor.GREEN + ".");
            getServer().broadcastMessage(BroadcastPreFix + "The Build Off has been opened. Do " + ChatColor.BLUE + "/join" + ChatColor.DARK_AQUA + " to enroll yourself!");
        }
        return true;
    }

    private boolean endBuildOff(CommandSender sender) {
        if (RunningBuildOff) {
            try {
                JoinableBuildOff = false;
                RunningBuildOff = false;
                AfterBuildOff = true;
                String worldName = getConfig().getString("startblock.world");
                RegionManager rgm = WGBukkit.getRegionManager(getServer().getWorld(worldName));
                ProtectedRegion rgContest = rgm.getRegion("contestcomplete");
                rgContest.setPriority(2);
                rgm.save();
                if (getConfig().getBoolean("stream.enabled")) {
                    getServer().broadcastMessage(BroadcastPreFix + "The Build Off has ended! Judging will commence soon. You can watch the judging live at: " + ChatColor.BLUE + getConfig().getString("streamlink"));
                } else {
                    getServer().broadcastMessage(BroadcastPreFix + "The Build Off has ended! Judging will commence soon.");
                }

            } catch (StorageException ex) {
                Logger.getLogger(BuildOffManager.class.getName()).log(Level.SEVERE, null, ex);
            }
        } else {
            sender.sendMessage(PreFix + ChatColor.RED + "There is no Build Off running that you can end.");
        }
        return true;
    }

    private void joinBuildOff(CommandSender sender) {
        int i = 0;
        for (String playerName : BuildOffContestants) {
            if (playerName.equals("")) {
                BuildOffContestants.set(i, sender.getName());
                break;
            }
            i++;
        }
        if (RunningBuildOff) {
            sender.sendMessage(PreFix + ChatColor.GREEN + "You have joined the Build Off! You can go to your plot using " + ChatColor.BLUE + "/tpplot" + ChatColor.GREEN + ". The theme is: " + ChatColor.BLUE + getConfig().getString("theme"));
        } else {
            sender.sendMessage(PreFix + ChatColor.GREEN + "You have joined the Build Off! You can go to your plot using " + ChatColor.BLUE + "/tpplot" + ChatColor.GREEN + ". You can do " + ChatColor.BLUE + "/leave" + ChatColor.GREEN + " to leave the Build Off.");
        }
        preparePlotSign(sender);
        updateBoard(sender);
        updateRegions(sender.getName());
    }

    private void leaveBuildOff(CommandSender sender) {
        int playerIndex = BuildOffContestants.indexOf(sender.getName());
        resetPlot(playerIndex);
        removeOneBoardSign(playerIndex);
        BuildOffContestants.set(playerIndex, "");
        sender.sendMessage(PreFix + ChatColor.GREEN + "You have left the Build Off! You can rejoin by using " + ChatColor.BLUE + "/join" + ChatColor.GREEN + ".");
    }

    private void preparePlotSign(CommandSender sender) {
        int plotNumber;
        plotNumber = BuildOffContestants.indexOf(sender.getName());
        Location boardSign;
        int pathWidth = getConfig().getInt("layout.pathwidth");
        int plotWidth = getConfig().getInt("layout.plotwidth");
        int plotsPerRow = getConfig().getInt("layout.plotsperrow");
        String worldName = getConfig().getString("startblock.world");
        int startX = getConfig().getInt("startblock.x");
        int startY = getConfig().getInt("startblock.y");
        int startZ = getConfig().getInt("startblock.z");
        boardSign = new Location(getServer().getWorld(worldName), startX - ((plotNumber % plotsPerRow) * (plotWidth + pathWidth)), (startY + 1), startZ + ((plotNumber / plotsPerRow) * (plotWidth + pathWidth)));
        boardSign.getBlock().setType(Material.SIGN_POST);
        boardSign.getBlock().setData((byte) 10);
        Sign sign;
        sign = (Sign) boardSign.getBlock().getState();
        String fancyPlotNumber = (ChatColor.DARK_BLUE + "<" + ChatColor.BLUE + Integer.toString(plotNumber + 1) + ChatColor.DARK_BLUE + ">");
        sign.setLine(0, fancyPlotNumber);
        sign.setLine(2, sender.getName());
        sign.update();
    }

    private void updateBoard(CommandSender sender) {
        int plotNumber;
        int plotsPerRow = getConfig().getInt("layout.plotsperrow");
        String worldName = getConfig().getString("boardstartblock.world");
        int boardStartX = getConfig().getInt("boardstartblock.x");
        int boardStartY = getConfig().getInt("boardstartblock.y");
        int boardStartZ = getConfig().getInt("boardstartblock.z");
        plotNumber = BuildOffContestants.indexOf(sender.getName());
        Location boardSign;
        boardSign = new Location(getServer().getWorld(worldName), boardStartX - ((plotNumber % plotsPerRow) * 1), boardStartY + ((plotNumber / plotsPerRow) * 1), boardStartZ);
        boardSign.getBlock().setType(Material.WALL_SIGN);
        boardSign.getBlock().setData((byte) 2);
        Sign sign;
        sign = (Sign) boardSign.getBlock().getState();
        String fancyPlotNumber = (ChatColor.DARK_BLUE + "<" + ChatColor.BLUE + Integer.toString(plotNumber + 1) + ChatColor.DARK_BLUE + ">");
        sign.setLine(0, fancyPlotNumber);
        sign.setLine(2, sender.getName());
        sign.update();
    }

    private void removeOneBoardSign(int plotNumber) {
        int plotsPerRow = getConfig().getInt("layout.plotsperrow");
        String worldName = getConfig().getString("boardstartblock.world");
        int boardStartX = getConfig().getInt("boardstartblock.x");
        int boardStartY = getConfig().getInt("boardstartblock.y");
        int boardStartZ = getConfig().getInt("boardstartblock.z");
        Location boardSign;
        boardSign = new Location(getServer().getWorld(worldName), boardStartX - ((plotNumber % plotsPerRow) * 1), boardStartY + ((plotNumber / plotsPerRow) * 1), boardStartZ);
        boardSign.getBlock().setType(Material.AIR);
    }

    private void updateThemeSign() {
        Location loc;
        String worldName = getConfig().getString("themesignblock.world");
        int themeBlockX = getConfig().getInt("themesignblock.x");
        int themeBlockY = getConfig().getInt("themesignblock.y");
        int themeBlockZ = getConfig().getInt("themesignblock.z");
        loc = new Location(getServer().getWorld(worldName), themeBlockX, themeBlockY, themeBlockZ);
        loc.getBlock().setType(Material.WALL_SIGN);
        loc.getBlock().setData((byte) 2);
        String subString1 = getConfig().getString("theme");
        Sign sign;
        sign = (Sign) loc.getBlock().getState();
        sign.setLine(0, "=-=-=-=-=-=-=-=");
        sign.setLine(1, ChatColor.DARK_AQUA + "" + ChatColor.BOLD + subString1);
        sign.setLine(2, ChatColor.DARK_AQUA + "");
        sign.setLine(3, "=-=-=-=-=-=-=-=");
        sign.update();
    }

    private void updateRegions(String name) {
        try {
            int plotNumber;
            plotNumber = BuildOffContestants.indexOf(name);
            String worldName = getConfig().getString("startblock.world");
            RegionManager rgm = WGBukkit.getRegionManager(getServer().getWorld(worldName));
            ProtectedRegion rgSmall = rgm.getRegion("plotbig" + Integer.toString(plotNumber));
            ProtectedRegion rgBig = rgm.getRegion("plotsmall" + Integer.toString(plotNumber));
            DefaultDomain dd = new DefaultDomain();
            dd.addPlayer(name.toLowerCase());
            rgSmall.setMembers(dd);
            rgBig.setMembers(dd);
            rgm.addRegion(rgBig);
            rgm.addRegion(rgSmall);
            rgm.save();
        } catch (StorageException ex) {
            Logger.getLogger(BuildOffManager.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void resetBoard() {
        int maxPlotNumber = getConfig().getInt("buildoff.maxcontestants") - 1;
        int plotsPerRow = getConfig().getInt("layout.plotsperrow");
        String worldName = getConfig().getString("boardstartblock.world");
        int boardStartX = getConfig().getInt("boardstartblock.x");
        int boardStartY = getConfig().getInt("boardstartblock.y");
        int boardStartZ = getConfig().getInt("boardstartblock.z");
        Location l1, l2;
        l1 = new Location(getServer().getWorld(worldName), boardStartX, boardStartY, boardStartZ);
        l2 = new Location(getServer().getWorld(worldName), boardStartX - (plotsPerRow - 1), boardStartY + ((maxPlotNumber / plotsPerRow) * 1), boardStartZ);
        setBlocks(l1, l2, Material.AIR);
    }

    private void resetThemeSign() {
        Location loc;
        String worldName = getConfig().getString("themesignblock.world");
        int themeBlockX = getConfig().getInt("themesignblock.x");
        int themeBlockY = getConfig().getInt("themesignblock.y");
        int themeBlockZ = getConfig().getInt("themesignblock.z");
        loc = new Location(getServer().getWorld(worldName), themeBlockX, themeBlockY, themeBlockZ);
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
            String worldName = getConfig().getString("startblock.world");
            int startX = getConfig().getInt("startblock.x");
            int startY = getConfig().getInt("startblock.y");
            int startZ = getConfig().getInt("startblock.z");
            final int deltaX = (number % plotsPerRow) * (plotWidth + pathWidth);
            final int deltaZ = (number / plotsPerRow) * (plotWidth + pathWidth);
            final int deltaPlot = plotWidth - 2;
            Location l1 = new Location(getServer().getWorld(worldName), (startX - deltaPlot) - (deltaX), (startY - 5), (startZ + deltaPlot) + deltaZ);
            Location l2 = new Location(getServer().getWorld(worldName), (startX - 1) - (deltaX), 1, (startZ + 1) + deltaZ);
            setBlocks(l1, l2, Material.STONE);
            Location l3 = new Location(getServer().getWorld(worldName), (startX - deltaPlot) - (deltaX), (startY - 2), (startZ + deltaPlot) + deltaZ);
            Location l4 = new Location(getServer().getWorld(worldName), (startX - 1) - (deltaX), (startY - 4), (startZ + 1) + deltaZ);
            setBlocks(l3, l4, Material.DIRT);
            Location l5 = new Location(getServer().getWorld(worldName), (startX - deltaPlot) - (deltaX), (startY - 1), (startZ + deltaPlot) + deltaZ);
            Location l6 = new Location(getServer().getWorld(worldName), (startX - 1) - (deltaX), (startY - 1), (startZ + 1) + deltaZ);
            setBlocks(l5, l6, Material.GRASS);
            Location l7 = new Location(getServer().getWorld(worldName), (startX - (plotWidth - 1)) - (deltaX), startY, (startZ + (plotWidth - 1)) + deltaZ);
            Location l8 = new Location(getServer().getWorld(worldName), startX - (deltaX), 64, startZ + deltaZ);
            setBlocks(l7, l8, Material.STEP);
            Location l9 = new Location(getServer().getWorld(worldName), (startX - deltaPlot) - (deltaX), 255, (startZ + deltaPlot) + deltaZ);
            Location l10 = new Location(getServer().getWorld(worldName), (startX - 1) - (deltaX), startY, (startZ + 1) + deltaZ);
            setBlocks(l9, l10, Material.AIR);
            Location l11 = new Location(getServer().getWorld(worldName), startX - (deltaX), startY, startZ + deltaZ);
            getServer().getWorld(worldName).getBlockAt(l11).setType(Material.GLOWSTONE);
            Location plotSign;
            plotSign = new Location(getServer().getWorld(worldName), startX - (deltaX), (startY + 1), startZ + deltaZ);
            plotSign.getBlock().setType(Material.SIGN_POST);
            plotSign.getBlock().setData((byte) 10);
            Sign sign;
            sign = (Sign) plotSign.getBlock().getState();
            String fancyPlotNumber = (ChatColor.DARK_BLUE + "<" + ChatColor.BLUE + Integer.toString(number + 1) + ChatColor.DARK_BLUE + ">");
            sign.setLine(0, fancyPlotNumber);
            sign.setLine(2, "");
            sign.update();

            //Make sign say [RESET]
            Location boardSign;
            worldName = getConfig().getString("boardstartblock.world");
            int boardStartX = getConfig().getInt("boardstartblock.x");
            int boardStartY = getConfig().getInt("boardstartblock.y");
            int boardStartZ = getConfig().getInt("boardstartblock.z");
            boardSign = new Location(getServer().getWorld(worldName), boardStartX - ((number % plotsPerRow) * 1), boardStartY + ((number / plotsPerRow) * 1), boardStartZ);
            boardSign.getBlock().setType(Material.WALL_SIGN);
            boardSign.getBlock().setData((byte) 2);
            Sign sign2;
            sign2 = (Sign) boardSign.getBlock().getState();
            sign2.setLine(1, (ChatColor.DARK_GRAY + "[RESET]"));
            sign2.update();

            //Reset the regions
            RegionManager rgm = WGBukkit.getRegionManager(getServer().getWorld(worldName));
            BlockVector bv1 = new BlockVector(l2.getBlockX(), l2.getBlockY(), l2.getBlockZ());
            BlockVector bv2 = new BlockVector(l9.getBlockX(), l9.getBlockY(), l9.getBlockZ());
            ProtectedCuboidRegion pcr1 = new ProtectedCuboidRegion(("plotbig" + Integer.toString(number)), bv1, bv2);
            BlockVector bv3 = new BlockVector(l7.getBlockX(), l7.getBlockY(), l7.getBlockZ());
            BlockVector bv4 = new BlockVector(l8.getBlockX(), l8.getBlockY(), l8.getBlockZ());
            ProtectedCuboidRegion pcr2 = new ProtectedCuboidRegion(("plotsmall" + Integer.toString(number)), bv3, bv4);
            pcr1.setPriority(1);
            pcr2.setPriority(1);
            rgm.addRegion(pcr1);
            rgm.addRegion(pcr2);
            rgm.save();
        } catch (StorageException ex) {
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
            String worldName = getConfig().getString("startblock.world");
            int startX = getConfig().getInt("startblock.x");
            int startY = getConfig().getInt("startblock.y");
            int startZ = getConfig().getInt("startblock.z");
            final int deltaX = (number % plotsPerRow) * (plotWidth + pathWidth);
            final int deltaZ = (number / plotsPerRow) * (plotWidth + pathWidth);
            final int deltaPlot = plotWidth - 2;
            Location l1 = new Location(getServer().getWorld(worldName), (startX - deltaPlot) - (deltaX), (startY - 5), (startZ + deltaPlot) + deltaZ);
            Location l2 = new Location(getServer().getWorld(worldName), (startX - 1) - (deltaX), 1, (startZ + 1) + deltaZ);
            setBlocks(l1, l2, Material.STONE);
            Location l3 = new Location(getServer().getWorld(worldName), (startX - deltaPlot) - (deltaX), (startY - 2), (startZ + deltaPlot) + deltaZ);
            Location l4 = new Location(getServer().getWorld(worldName), (startX - 1) - (deltaX), (startY - 4), (startZ + 1) + deltaZ);
            setBlocks(l3, l4, Material.DIRT);
            Location l5 = new Location(getServer().getWorld(worldName), (startX - deltaPlot) - (deltaX), (startY - 1), (startZ + deltaPlot) + deltaZ);
            Location l6 = new Location(getServer().getWorld(worldName), (startX - 1) - (deltaX), (startY - 1), (startZ + 1) + deltaZ);
            setBlocks(l5, l6, Material.GRASS);
            Location l7 = new Location(getServer().getWorld(worldName), (startX - (plotWidth - 1)) - (deltaX), startY, (startZ + (plotWidth - 1)) + deltaZ);
            Location l8 = new Location(getServer().getWorld(worldName), startX - (deltaX), startY, startZ + deltaZ);
            setBlocks(l7, l8, Material.STEP);
            Location l9 = new Location(getServer().getWorld(worldName), (startX - deltaPlot) - (deltaX), 255, (startZ + deltaPlot) + deltaZ);
            Location l10 = new Location(getServer().getWorld(worldName), (startX - 1) - (deltaX), startY, (startZ + 1) + deltaZ);
            setBlocks(l9, l10, Material.AIR);
            Location l11 = new Location(getServer().getWorld(worldName), startX - (deltaX), startY, startZ + deltaZ);
            getServer().getWorld(worldName).getBlockAt(l11).setType(Material.GLOWSTONE);
            Location plotSign;
            plotSign = new Location(getServer().getWorld(worldName), startX - (deltaX), (startY + 1), startZ + deltaZ);
            plotSign.getBlock().setType(Material.SIGN_POST);
            plotSign.getBlock().setData((byte) 10);
            Sign sign;
            sign = (Sign) plotSign.getBlock().getState();
            String fancyPlotNumber = (ChatColor.DARK_BLUE + "<" + ChatColor.BLUE + Integer.toString(number + 1) + ChatColor.DARK_BLUE + ">");
            sign.setLine(0, fancyPlotNumber);
            sign.setLine(2, "");
            sign.update();

            RegionManager rgm = WGBukkit.getRegionManager(getServer().getWorld(worldName));
            BlockVector bv1 = new BlockVector(l2.getBlockX(), l2.getBlockY(), l2.getBlockZ());
            BlockVector bv2 = new BlockVector(l9.getBlockX(), l9.getBlockY(), l9.getBlockZ());
            ProtectedCuboidRegion pcr1 = new ProtectedCuboidRegion(("plotbig" + Integer.toString(number)), bv1, bv2);
            BlockVector bv3 = new BlockVector(l7.getBlockX(), l7.getBlockY(), l7.getBlockZ());
            BlockVector bv4 = new BlockVector(l8.getBlockX(), l8.getBlockY(), l8.getBlockZ());
            List<BlockVector2D> bv2dList = new ArrayList<>();
            bv2dList.add(new BlockVector2D(l7.getBlockX(), l7.getBlockZ()));
            bv2dList.add(new BlockVector2D(l7.getBlockX(), l7.getBlockZ() - plotWidth+1));
            bv2dList.add(new BlockVector2D(l8.getBlockX() - 1, l8.getBlockZ()));
            bv2dList.add(new BlockVector2D(l8.getBlockX(), l8.getBlockZ() + 1));
            bv2dList.add(new BlockVector2D(l7.getBlockX() + plotWidth-1, l7.getBlockZ()));
            ProtectedPolygonalRegion ppr1 = new ProtectedPolygonalRegion(("plotsmall" + Integer.toString(number)), bv2dList, l7.getBlockY(), l7.getBlockY());
            pcr1.setPriority(1);
            ppr1.setPriority(1);
            rgm.addRegion(pcr1);
            rgm.addRegion(ppr1);
            rgm.save();
        } catch (StorageException ex) {
            Logger.getLogger(BuildOffManager.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void createCompleteRegion() {
        int pathWidth = getConfig().getInt("layout.pathwidth");
        int plotWidth = getConfig().getInt("layout.plotwidth");
        int plotsPerRow = getConfig().getInt("layout.plotsperrow");
        String worldName = getConfig().getString("startblock.world");
        int startX = getConfig().getInt("startblock.x");
        int startZ = getConfig().getInt("startblock.z");
        RegionManager rgm = WGBukkit.getRegionManager(getServer().getWorld(worldName));
        BlockVector bv5 = new BlockVector(startX, 0, startZ);
        int sizetemp = (plotsPerRow * plotWidth) + ((plotsPerRow - 1) * pathWidth) - 1;
        BlockVector bv6 = new BlockVector((startX - sizetemp), 255, startZ + sizetemp);
        ProtectedCuboidRegion pcr3 = new ProtectedCuboidRegion("contestcomplete", bv5, bv6);
        pcr3.setPriority(0);
        rgm.addRegion(pcr3);
        pcr3.setFlag(DefaultFlag.BUILD, State.DENY);
        try {
            rgm.save();
        } catch (StorageException ex) {
            Logger.getLogger(BuildOffManager.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void fillContestantsList() {
        while (BuildOffContestants.size() < getConfig().getInt("buildoff.maxcontestants")) {
            BuildOffContestants.add("");
        }
    }

    private boolean isFullBuildOff() {
        for (String playerName : BuildOffContestants) {
            if (playerName.equals("")) {
                return false;
            }
        }
        return true;
    }
}
