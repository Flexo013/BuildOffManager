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
        int direction = getConfig().getInt("layout.direction");
        if (direction > 3 || direction < 0) {
            getConfig().set("layout.direction", 0);
            log.severe(PreFix + "Illegal direction found in config.yml: layout.direction. Reset to default value (0)");
        }
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

        // Opens a new Build Off so people are allowed to do /join
        if (cmd.getName().equalsIgnoreCase("openbuildoff")) {
            return openBuildOff(sender);
        }

        // Ends the current Build Off.
        if (cmd.getName().equalsIgnoreCase("endbuildoff")) {
            return endBuildOff(sender);
        }

        // Resets the Build Off plots and clear the contestant list
        if (cmd.getName().equalsIgnoreCase("resetbuildoff")) {
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

        // Lists all players who are online, coloring them green if they already joined
        if (cmd.getName().equalsIgnoreCase("listplayers")) {
            if (!JoinableBuildOff) {
                sender.sendMessage(PreFix + ChatColor.GOLD + "Currently nobody can join the Build Off.");
            } else {
                String playerString;
                playerString = PreFix + ChatColor.GOLD + "List of online players:" + ChatColor.YELLOW;
                for (Player player : sender.getServer().getOnlinePlayers()) {
                    String playerName = player.getName();
                    if (BuildOffContestants.contains(playerName)) {
                        playerString = playerString + " " + ChatColor.GREEN + playerName;
                    } else {
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
                sender.sendMessage(PreFix + "The /join command can only be used by players.");
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
                        leaveBuildOff(sender.getName());
                        sender.sendMessage(PreFix + ChatColor.GREEN + "You have left the Build Off! You can rejoin by using " + ChatColor.BLUE + "/join" + ChatColor.GREEN + ".");
                    } else {
                        sender.sendMessage(PreFix + ChatColor.RED + "You can only leave the Build Off before it starts.");
                    }
                } else {
                    sender.sendMessage(PreFix + ChatColor.RED + "You are not enrolled for the Build Off, so you cannot leave the Build Off.");
                }
            }
            return true;
        }

        //Forces a player to leave the Build Off
        if (cmd.getName().equalsIgnoreCase("forceleave")) {
            if (args.length == 1) {
                if (BuildOffContestants.contains(args[0])) {
                    if (!AfterBuildOff) {
                        leaveBuildOff(args[0]);
                        sender.sendMessage(PreFix + ChatColor.GREEN + "You have forced " + ChatColor.YELLOW + args[0] + ChatColor.GREEN + " to leave the Build Off.");
                    } else {
                        sender.sendMessage(PreFix + ChatColor.RED + "Use /resetplot to remove players after the Build Off.");
                    }
                } else {
                    sender.sendMessage(PreFix + ChatColor.YELLOW + args[0] + ChatColor.RED + " is not enrolled in the Build Off.");
                }
                return true;
            } else {
                return false;
            }
        }

        // Teleports a player to a plot
        if (cmd.getName().equalsIgnoreCase("tpplot")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(PreFix + ChatColor.RED + "The /tpplot command can only be used by players.");
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
                    if (args[0].matches("[0-9]+")) {
                        if (getConfig().getInt("buildoff.maxcontestants") >= Integer.parseInt(args[0]) && Integer.parseInt(args[0]) > 0) {
                            tpToPlot((Integer.parseInt(args[0]) - 1), player);
                        } else {
                            sender.sendMessage(PreFix + ChatColor.RED + "Choose a plot number between 1 and " + getConfig().getString("buildoff.maxcontestants") + ".");
                        }
                    } else {
                        String targetPlotOwner = args[0];
                        if (BuildOffContestants.contains(targetPlotOwner)) {
                            tpToPlot(targetPlotOwner, player);
                        } else {
                            sender.sendMessage(PreFix + ChatColor.DARK_RED + targetPlotOwner + ChatColor.RED + " is not enrolled for the Build Off. So you cannot be teleported to their plot.");
                        }
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

        //Initializes the Build Off plots
        if (cmd.getName().equalsIgnoreCase("bominit")) {
            int max = getConfig().getInt("buildoff.maxcontestants");
            for (int i = 0; i < max; i++) {
                initializePlot(i);
            }
            createCompleteRegion();
            sender.sendMessage(PreFix + ChatColor.GREEN + "Initializing Build Off complete!");
            return true;
        }

        //Creates x more plots
        if (cmd.getName().equalsIgnoreCase("expandbo")) {
            if (!(args.length == 1)) {
                return false;
            } else {
                int oldsize = getConfig().getInt("buildoff.maxcontestants");
                int newAmountOfPlots = Integer.parseInt(args[0]);
                int newsize = oldsize + newAmountOfPlots;
                getConfig().set("buildoff.maxcontestants", newsize);
                saveConfig();
                for (int i = oldsize; i < newsize; i++) {
                    initializePlot(i);
                }
                sender.sendMessage(PreFix + ChatColor.RED + "Do not forget to manually expand the contestcomplete region!");
                return true;
            }
        }

        //Reloads the config
        if (cmd.getName().equalsIgnoreCase("reloadbom")) {
            reloadConfig();
            sender.sendMessage(PreFix + ChatColor.GREEN + "You have successfully reloaded the BuildOffManager config.");
            return true;
        }

        //Displays Build Off basic help
        if (cmd.getName().equalsIgnoreCase("bohelp")) {
            sender.sendMessage(ChatColor.YELLOW + " ---- " + ChatColor.GOLD + "Build Off Help" + ChatColor.YELLOW + " ---- \n"
                    + ChatColor.GOLD + "/join" + ChatColor.RESET + ": Join the Build Off.\n"
                    + ChatColor.GOLD + "/leave" + ChatColor.RESET + ": Leave the Build Off.\n"
                    + ChatColor.GOLD + "/tpplot" + ChatColor.RESET + ": Teleport to your plot.\n"
                    + ChatColor.GOLD + "/tpplot <name>" + ChatColor.RESET + ": Teleport to the plot of <name>.\n"
                    + ChatColor.GOLD + "/theme" + ChatColor.RESET + ": Displays the Theme of the Build Off.\n"
                    + ChatColor.GOLD + "/bohelp" + ChatColor.RESET + ": Displays this help message.");
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
            int direction = getConfig().getInt("boardstartblock.direction");
            int boardStartX = getConfig().getInt("boardstartblock.x");
            int boardStartY = getConfig().getInt("boardstartblock.y");
            int boardStartZ = getConfig().getInt("boardstartblock.z");
            Location l1 = new Location(
                    getServer().getWorld(worldName),
                    boardStartX,
                    boardStartY,
                    boardStartZ
            );
            Location l2 = new Location(
                    getServer().getWorld(worldName),
                    boardStartX + getX(plotsPerRow - 1, direction),
                    boardStartY + ((maxPlotNumber / plotsPerRow) * 1),
                    boardStartZ
            );
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
        int plotSize = getConfig().getInt("layout.plotsize");
        int plotsPerRow = getConfig().getInt("layout.plotsperrow");
        String worldName = getConfig().getString("startblock.world");
        int direction = getConfig().getInt("layout.direction");
        int startX = getConfig().getInt("startblock.x");
        int startY = getConfig().getInt("startblock.y");
        int startZ = getConfig().getInt("startblock.z");
        teleLoc = new Location(
                getServer().getWorld(worldName),
                (startX + 0.5) + getX((plotNumber % plotsPerRow) * (plotSize + pathWidth), direction),
                startY,
                (startZ + 0.5) + getZ((plotNumber / plotsPerRow) * (plotSize + pathWidth) - 3, direction)
        );
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
                String buildOffDuration = getConfig().getString("buildoff.duration");
                getServer().broadcastMessage(BroadcastPreFix + "The Build Off has started! You will have " + buildOffDuration + " to complete your build. The theme is: " + ChatColor.BLUE + ChatColor.BOLD + getConfig().getString("theme"));
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
                    getServer().broadcastMessage(BroadcastPreFix + "The Build Off has ended! Judging will commence soon. You can watch the judging live at: " + ChatColor.BLUE + getConfig().getString("stream.link"));
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

    private void leaveBuildOff(String playername) {
        int playerIndex = BuildOffContestants.indexOf(playername);
        resetPlot(playerIndex);
        removeOneBoardSign(playerIndex);
        BuildOffContestants.set(playerIndex, "");
    }

    private void preparePlotSign(CommandSender sender) {
        int plotNumber;
        plotNumber = BuildOffContestants.indexOf(sender.getName());
        int direction = getConfig().getInt("layout.direction");
        int pathWidth = getConfig().getInt("layout.pathwidth");
        int plotSize = getConfig().getInt("layout.plotsize");
        int plotsPerRow = getConfig().getInt("layout.plotsperrow");
        String worldName = getConfig().getString("startblock.world");
        int startX = getConfig().getInt("startblock.x");
        int startY = getConfig().getInt("startblock.y");
        int startZ = getConfig().getInt("startblock.z");
        Location plotSign = new Location(
                getServer().getWorld(worldName),
                startX + getX((plotNumber % plotsPerRow) * (plotSize + pathWidth), direction),
                (startY + 1),
                startZ + getZ((plotNumber / plotsPerRow) * (plotSize + pathWidth), direction)
        );
        plotSign.getBlock().setType(Material.SIGN_POST);
        plotSign.getBlock().setData(getSignDirection(direction));
        Sign sign = (Sign) plotSign.getBlock().getState();
        String fancyPlotNumber = (ChatColor.DARK_BLUE + "<" + ChatColor.BLUE + Integer.toString(plotNumber + 1) + ChatColor.DARK_BLUE + ">");
        sign.setLine(0, fancyPlotNumber);
        sign.setLine(2, sender.getName());
        sign.update();
    }

    private void updateBoard(CommandSender sender) {
        int plotNumber;
        int plotsPerRow = getConfig().getInt("layout.plotsperrow");
        int direction = getConfig().getInt("boardstartblock.direction");
        String worldName = getConfig().getString("boardstartblock.world");
        int boardStartX = getConfig().getInt("boardstartblock.x");
        int boardStartY = getConfig().getInt("boardstartblock.y");
        int boardStartZ = getConfig().getInt("boardstartblock.z");
        plotNumber = BuildOffContestants.indexOf(sender.getName());
        Location boardSign = new Location(
                getServer().getWorld(worldName),
                boardStartX + getX((plotNumber % plotsPerRow) * 1, direction),
                boardStartY + (plotNumber / plotsPerRow) * 1,
                boardStartZ
        );
        boardSign.getBlock().setType(Material.WALL_SIGN);
        boardSign.getBlock().setData(getBoardSignDirection(direction));
        Sign sign;
        sign = (Sign) boardSign.getBlock().getState();
        String fancyPlotNumber = (ChatColor.DARK_BLUE + "<" + ChatColor.BLUE + Integer.toString(plotNumber + 1) + ChatColor.DARK_BLUE + ">");
        sign.setLine(0, fancyPlotNumber);
        sign.setLine(2, sender.getName());
        sign.update();
    }

    private void removeOneBoardSign(int plotNumber) {
        int plotsPerRow = getConfig().getInt("layout.plotsperrow");
        int direction = getConfig().getInt("boardstartblock.direction");
        String worldName = getConfig().getString("boardstartblock.world");
        int boardStartX = getConfig().getInt("boardstartblock.x");
        int boardStartY = getConfig().getInt("boardstartblock.y");
        int boardStartZ = getConfig().getInt("boardstartblock.z");
        Location boardSign = new Location(
                getServer().getWorld(worldName),
                boardStartX + getX((plotNumber % plotsPerRow) * 1, direction),
                boardStartY + ((plotNumber / plotsPerRow) * 1),
                boardStartZ);
        boardSign.getBlock().setType(Material.AIR);
    }

    private void updateThemeSign() {
        int direction = getConfig().getInt("boardstartblock.direction");
        String worldName = getConfig().getString("themesignblock.world");
        int themeBlockX = getConfig().getInt("themesignblock.x");
        int themeBlockY = getConfig().getInt("themesignblock.y");
        int themeBlockZ = getConfig().getInt("themesignblock.z");
        Location loc = new Location(
                getServer().getWorld(worldName),
                themeBlockX,
                themeBlockY,
                themeBlockZ
        );
        loc.getBlock().setType(Material.WALL_SIGN);
        loc.getBlock().setData(getBoardSignDirection(direction));
        String themeString = getConfig().getString("theme");
        Sign sign = (Sign) loc.getBlock().getState();
        sign.setLine(0, "=-=-=-=-=-=-=-=");
        sign.setLine(1, ChatColor.DARK_AQUA + "" + ChatColor.BOLD + themeString);
        sign.setLine(2, ChatColor.DARK_AQUA + "");
        sign.setLine(3, "=-=-=-=-=-=-=-=");
        sign.update();
    }

    private void updateRegions(String name) {
        int plotNumber;
        plotNumber = BuildOffContestants.indexOf(name);
        String worldName = getConfig().getString("startblock.world");
        RegionManager rgm = WGBukkit.getRegionManager(getServer().getWorld(worldName));
        ProtectedRegion rgBig = rgm.getRegion("plotbig" + Integer.toString(plotNumber));
        ProtectedRegion rgSmall = rgm.getRegion("plotsmall" + Integer.toString(plotNumber));
        DefaultDomain dd = new DefaultDomain();
        dd.addPlayer(name.toLowerCase());
        rgBig.setMembers(dd);
        rgSmall.setMembers(dd);
        rgm.addRegion(rgSmall);
        rgm.addRegion(rgBig);
        try {
            rgm.save();
        } catch (StorageException ex) {
            Logger.getLogger(BuildOffManager.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void resetBoard() {
        int maxPlotNumber = getConfig().getInt("buildoff.maxcontestants") - 1;
        int direction = getConfig().getInt("boardstartblock.direction");
        int plotsPerRow = getConfig().getInt("layout.plotsperrow");
        String worldName = getConfig().getString("boardstartblock.world");
        int boardStartX = getConfig().getInt("boardstartblock.x");
        int boardStartY = getConfig().getInt("boardstartblock.y");
        int boardStartZ = getConfig().getInt("boardstartblock.z");
        Location l1 = new Location(
                getServer().getWorld(worldName),
                boardStartX,
                boardStartY,
                boardStartZ
        );
        Location l2 = new Location(
                getServer().getWorld(worldName),
                boardStartX + getX(plotsPerRow - 1, direction),
                boardStartY + ((maxPlotNumber / plotsPerRow) * 1),
                boardStartZ
        );
        setBlocks(l1, l2, Material.AIR);
    }

    private void resetThemeSign() {
        String worldName = getConfig().getString("themesignblock.world");
        int direction = getConfig().getInt("boardstartblock.direction");
        int themeBlockX = getConfig().getInt("themesignblock.x");
        int themeBlockY = getConfig().getInt("themesignblock.y");
        int themeBlockZ = getConfig().getInt("themesignblock.z");
        Location loc = new Location(getServer().getWorld(worldName), themeBlockX, themeBlockY, themeBlockZ);
        loc.getBlock().setType(Material.WALL_SIGN);
        loc.getBlock().setData(getBoardSignDirection(direction));
        Sign sign = (Sign) loc.getBlock().getState();
        sign.setLine(0, "=-=-=-=-=-=-=-=");
        sign.setLine(1, ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Secret till");
        sign.setLine(2, ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "the start");
        sign.setLine(3, "=-=-=-=-=-=-=-=");
        sign.update();
    }

    private void resetPlot(int number) {
        setPlotBlocks(number);

        //Make sign say [RESET]
        String worldName = getConfig().getString("boardstartblock.world");
        int plotsPerRow = getConfig().getInt("layout.plotsperrow");
        int direction = getConfig().getInt("boardstartblock.direction");
        int boardStartX = getConfig().getInt("boardstartblock.x");
        int boardStartY = getConfig().getInt("boardstartblock.y");
        int boardStartZ = getConfig().getInt("boardstartblock.z");
        Location boardSignLoc = new Location(
                getServer().getWorld(worldName),
                boardStartX + getX((number % plotsPerRow) * 1, direction),
                boardStartY + ((number / plotsPerRow) * 1),
                boardStartZ
        );
        boardSignLoc.getBlock().setType(Material.WALL_SIGN);
        boardSignLoc.getBlock().setData(getBoardSignDirection(direction));
        Sign sign = (Sign) boardSignLoc.getBlock().getState();
        sign.setLine(1, (ChatColor.GRAY + "[RESET]"));
        sign.update();

        //Reset the regions
        RegionManager rgm = WGBukkit.getRegionManager(getServer().getWorld(worldName));
        DefaultDomain dd = new DefaultDomain();
        ProtectedRegion plotBig = rgm.getRegion("plotbig" + number);
        ProtectedRegion plotSmall = rgm.getRegion("plotsmall" + number);
        if (plotBig != null && plotSmall != null) {
            plotBig.setMembers(dd);
            plotSmall.setMembers(dd);
        } else {
            log.log(Level.SEVERE, "{0}Contest regions of plot <{1}> were not found. ", new Object[]{PreFix, number + 1});
        }
        try {
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

    private int getX(int x, int direction) {
        if ((direction & 2) == 2) {
            return -x;
        }
        return x;
    }

    private int getZ(int z, int direction) {
        if ((direction & 1) == 1) {
            return -z;
        }
        return z;
    }

    private byte getSignDirection(int direction) {
        switch (direction) {
            case 0:
                return (byte) 6;
            case 1:
                return (byte) 2;
            case 2:
                return (byte) 10;
            case 3:
                return (byte) 14;
        }
        return 0;
    }

    private byte getBoardSignDirection(int direction) {
        switch (direction) {
            case 0:
                return (byte) 4;
            case 1:
                return (byte) 3;
            case 2:
                return (byte) 2;
            case 3:
                return (byte) 5;
        }
        return 0;
    }

    private Location setPlotBlocks(int number) {
        int direction = getConfig().getInt("layout.direction");
        int pathWidth = getConfig().getInt("layout.pathwidth");
        int outerPlotSize = getConfig().getInt("layout.plotsize");
        int plotsPerRow = getConfig().getInt("layout.plotsperrow");
        String worldName = getConfig().getString("startblock.world");
        int startX = getConfig().getInt("startblock.x");
        int startY = getConfig().getInt("startblock.y");
        int startZ = getConfig().getInt("startblock.z");
        final int offsetX = (number % plotsPerRow) * (outerPlotSize + pathWidth);
        final int offsetZ = (number / plotsPerRow) * (outerPlotSize + pathWidth);
        final int innerPlotSize = outerPlotSize - 2;

        Location bedrockL1 = new Location(
                getServer().getWorld(worldName),
                startX + getX(innerPlotSize + offsetX, direction),
                0,
                startZ + getZ(innerPlotSize + offsetZ, direction)
        );
        Location bedrockL2 = new Location(
                getServer().getWorld(worldName),
                startX + getX(1 + offsetX, direction),
                0,
                startZ + getZ(1 + offsetZ, direction)
        );
        setBlocks(bedrockL1, bedrockL2, Material.BEDROCK);

        Location stoneL1 = new Location(
                getServer().getWorld(worldName),
                startX + getX(innerPlotSize + offsetX, direction),
                (startY - 5),
                startZ + getZ(innerPlotSize + offsetZ, direction)
        );
        Location stoneL2 = new Location(
                getServer().getWorld(worldName),
                startX + getX(1 + offsetX, direction),
                1,
                startZ + getZ(1 + offsetZ, direction)
        );
        setBlocks(stoneL1, stoneL2, Material.STONE);

        Location dirtL1 = new Location(
                getServer().getWorld(worldName),
                startX + getX(innerPlotSize + offsetX, direction),
                (startY - 2),
                startZ + getZ(innerPlotSize + offsetZ, direction)
        );
        Location dirtL2 = new Location(
                getServer().getWorld(worldName),
                startX + getX(1 + offsetX, direction),
                (startY - 4),
                startZ + getZ(1 + offsetZ, direction)
        );
        setBlocks(dirtL1, dirtL2, Material.DIRT);

        Location grassL1 = new Location(
                getServer().getWorld(worldName),
                startX + getX(innerPlotSize + offsetX, direction),
                (startY - 1),
                startZ + getZ(innerPlotSize + offsetZ, direction)
        );
        Location grassL2 = new Location(
                getServer().getWorld(worldName),
                startX + getX(1 + offsetX, direction),
                (startY - 1),
                startZ + getZ(1 + offsetZ, direction)
        );
        setBlocks(grassL1, grassL2, Material.GRASS);

        Location stepL1 = new Location(
                getServer().getWorld(worldName),
                startX + getX((outerPlotSize - 1) + offsetX, direction),
                startY,
                startZ + getZ((outerPlotSize - 1) + offsetZ, direction)
        );
        Location stepL2 = new Location(
                getServer().getWorld(worldName),
                startX + getX(offsetX, direction),
                startY,
                startZ + getZ(offsetZ, direction)
        );
        setBlocks(stepL1, stepL2, Material.STEP);

        Location airL1 = new Location(
                getServer().getWorld(worldName),
                startX + getX(innerPlotSize + offsetX, direction),
                255,
                startZ + getZ(innerPlotSize + offsetZ, direction)
        );
        Location airL2 = new Location(
                getServer().getWorld(worldName),
                startX + getX(1 + offsetX, direction),
                startY,
                startZ + getZ(1 + offsetZ, direction)
        );
        setBlocks(airL1, airL2, Material.AIR);

        Location glowstoneL = new Location(
                getServer().getWorld(worldName),
                startX + getX(offsetX, direction),
                startY,
                startZ + getZ(offsetZ, direction)
        );
        getServer().getWorld(worldName).getBlockAt(glowstoneL).setType(Material.GLOWSTONE);

        Location plotSign = new Location(
                getServer().getWorld(worldName),
                startX + getX(offsetX, direction),
                (startY + 1),
                startZ + getZ(offsetZ, direction)
        );
        plotSign.getBlock().setType(Material.SIGN_POST);
        plotSign.getBlock().setData(getSignDirection(direction));

        Sign sign = (Sign) plotSign.getBlock().getState();
        String fancyPlotNumber = (ChatColor.DARK_BLUE + "<" + ChatColor.BLUE + Integer.toString(number + 1) + ChatColor.DARK_BLUE + ">");
        sign.setLine(0, fancyPlotNumber);
        sign.setLine(2, "");
        sign.update();
        return glowstoneL;
    }

    private void initializePlot(int number) {
        int direction = getConfig().getInt("layout.direction");
        String worldName = getConfig().getString("startblock.world");
        int outerPlotSize = getConfig().getInt("layout.plotsize");
        final int innerPlotSize = outerPlotSize - 2;
        Location glowstoneL = setPlotBlocks(number);

        RegionManager rgm = WGBukkit.getRegionManager(getServer().getWorld(worldName));

        BlockVector bv1 = new BlockVector(
                glowstoneL.getBlockX() + getX(1, direction),
                1,
                glowstoneL.getBlockZ() + getZ(1, direction)
        );
        BlockVector bv2 = new BlockVector(
                glowstoneL.getBlockX() + getX(innerPlotSize, direction),
                255,
                glowstoneL.getBlockZ() + getZ(innerPlotSize, direction)
        );
        ProtectedCuboidRegion pcr1 = new ProtectedCuboidRegion(
                "plotbig" + number,
                bv1,
                bv2
        );

        List<BlockVector2D> bv2dList = new ArrayList<>();
        bv2dList.add(new BlockVector2D(
                glowstoneL.getBlockX() + getX(outerPlotSize - 1, direction),
                glowstoneL.getBlockZ() + getZ(outerPlotSize - 1, direction)
        ));
        bv2dList.add(new BlockVector2D(
                glowstoneL.getBlockX() + getX(outerPlotSize - 1, direction),
                glowstoneL.getBlockZ()
        ));
        bv2dList.add(new BlockVector2D(
                glowstoneL.getBlockX() + getX(1, direction),
                glowstoneL.getBlockZ()
        ));
        bv2dList.add(new BlockVector2D(
                glowstoneL.getBlockX() + getX(1, direction),
                glowstoneL.getBlockZ() + getZ(1, direction)
        ));
        bv2dList.add(new BlockVector2D(
                glowstoneL.getBlockX(),
                glowstoneL.getBlockZ() + getZ(1, direction)
        ));
        bv2dList.add(new BlockVector2D(
                glowstoneL.getBlockX(),
                glowstoneL.getBlockZ() + getZ(outerPlotSize - 1, direction)
        ));

        ProtectedPolygonalRegion ppr1 = new ProtectedPolygonalRegion(
                "plotsmall" + number,
                bv2dList,
                glowstoneL.getBlockY() - 1,
                glowstoneL.getBlockY()
        );

        pcr1.setPriority(1);
        ppr1.setPriority(1);
        rgm.addRegion(pcr1);
        rgm.addRegion(ppr1);
        try {
            rgm.save();
        } catch (StorageException ex) {
            Logger.getLogger(BuildOffManager.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void createCompleteRegion() {
        int direction = getConfig().getInt("layout.direction");
        int pathWidth = getConfig().getInt("layout.pathwidth");
        int plotSize = getConfig().getInt("layout.plotsize");
        int plotsPerRow = getConfig().getInt("layout.plotsperrow");
        String worldName = getConfig().getString("startblock.world");
        int startX = getConfig().getInt("startblock.x");
        int startZ = getConfig().getInt("startblock.z");

        RegionManager rgm = WGBukkit.getRegionManager(getServer().getWorld(worldName));

        BlockVector bv5 = new BlockVector(
                startX,
                0,
                startZ
        );

        int sizetemp = (plotsPerRow * plotSize) + ((plotsPerRow - 1) * pathWidth) - 1;
        BlockVector bv6 = new BlockVector(
                startX + getX(sizetemp, direction),
                255,
                startZ + getZ(sizetemp, direction)
        );

        ProtectedCuboidRegion pcr3 = new ProtectedCuboidRegion("contestcomplete", bv5, bv6);
        pcr3.setPriority(2);
        rgm.addRegion(pcr3);
        pcr3.setFlag(DefaultFlag.PASSTHROUGH, State.DENY);
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

    private boolean isNoContestants() {
        for (String playerName : BuildOffContestants) {
            if (!playerName.equals("")) {
                return false;
            }
        }
        return true;
    }
}
