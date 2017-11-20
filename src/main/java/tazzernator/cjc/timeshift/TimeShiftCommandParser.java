package tazzernator.cjc.timeshift;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.World;

import tazzernator.cjc.timeshift.TimeShiftConfiguration.ErrorType;
import tazzernator.cjc.timeshift.TimeShiftConfiguration.HelpType;
import tazzernator.cjc.timeshift.settings.LoopSetting;
import tazzernator.cjc.timeshift.settings.WorldSetting;

public class TimeShiftCommandParser implements CommandExecutor {
	private TimeShift instance;
	// show up in a lot of places. Actual strings only shows up here.
	private final static String cmd = "shift";
	private final static String time = "time";

	TimeShiftCommandParser(TimeShift instance) {
		this.instance = instance;
	}

	// --------------------- help ----------------------------
	//send player help based on permissions to player
	private void sendHelp(Player player) {
		if (checkStartup(player) && checkShift(player)) {// has both
			instance.getMessaging().sendHelp(player, HelpType.FULL);
		} else if (checkShift(player)) {// has shift only
			instance.getMessaging().sendHelp(player, HelpType.SHIFT);
		} else if (checkStartup(player)) {// has startup only
			instance.getMessaging().sendHelp(player, HelpType.STARTUP);
		} else {// has nothing
			instance.getMessaging().sendError(player, ErrorType.NO_PERMS, ""); // send no permissions error
		}
	}
	// Send console help to a commandsender
	private void sendHelp(CommandSender sender) {
		instance.getMessaging().sendHelp(sender, HelpType.CONSOLE); // send console help
	}
	// ----------------------- startShift helper -------------------------
	// start a shift on a world
	private void setShift(World w, LoopSetting loop, CommandSender sender) {
		String wname = w.getName();
		WorldSetting worldSetting = instance.getWorldSettings().get(wname);//get world setting
		
		if (worldSetting == null) {
			worldSetting = new WorldSetting();
			worldSetting.setWorldName(wname);
		}
		worldSetting.setLoopName(loop.getLoopName());//set new loop name
		instance.scheduleTimer(w, wname, worldSetting, loop); // always reschedule timer, cancels any old timers.
		instance.getMessaging().sendMessage(sender, wname, worldSetting.getLoopName(), false);// print result
	}
	// ------------------- startup command helper ---------------------
	private void setPersist(Player player, CommandSender sender, boolean isPlayer, String[] split) {
		if (!isPlayer || checkStartup(player)) { // Player with permission or console
			String subCmd = split.length > 1 ? instance.getCommandAliases().get(split[1].toLowerCase()) : null;// Loop to run on startup
			if (subCmd == null || subCmd.equals("TS ERR")) {// If "Startup" was the only argument
				instance.getMessaging().sendHelp(player, HelpType.STARTUP);
			} else if (split.length > 2) {// If there was more than one argument after startup it's multi-world
				for (int i = 2; i < split.length; i++) { // for each world listed
					World w = this.instance.getServer().getWorld(split[i]);// try to get a world for each worldname
					if (w != null) { // if a world by that name exists
						instance.persistentWriter(subCmd, w);// world exists, write persistent setting
						instance.getMessaging().sendMessage(sender, w.getName(), subCmd, true);// print result
					} else {// world doesn't exist
						instance.getMessaging().sendError(sender, ErrorType.WORLD_DNE, split[i]);
					}
				}
			} else {// length=2, player-world startup setting
				if (isPlayer) {
					World w = player.getWorld();
					instance.persistentWriter(subCmd, w); // write setting for player's world
					instance.getMessaging().sendMessage(sender, w.getName(), subCmd, true);// print result
				} else {// not a player, error
					instance.getMessaging().sendError(sender, ErrorType.CONSOLE_WORLD_NEEDED, "");
				}
			}
		} else {
			instance.getMessaging().sendError(sender, ErrorType.START_PERM, ""); // send no startup permissions error
		}
	}
	// ------------------------- cancel command helper ----------------------------
	private void stopShift(Player player, CommandSender sender, boolean isPlayer, String[] split) {
		if (!isPlayer || checkStop(player)) {
			if (split.length == 1) {// act on player's world
				if (isPlayer) {// player world stop
					String wname = player.getWorld().getName();
					instance.cancelShift(instance.getWorldSettings().get(wname));// cancel it
					instance.getMessaging().sendMessage(sender, wname, "", false);// print result
				} else {// consoles need worlds
					instance.getMessaging().sendError(sender, ErrorType.CONSOLE_WORLD_NEEDED, "");
				}
			} else {// it's a multi-world stop command
				for (int i = 1; i < split.length; i++) { // for each world listed
					World w = instance.getServer().getWorld(split[i]);// try to get a world for each worldname
					if (w != null) { // if a world by that name exists
						String wname = w.getName();
						instance.cancelShift(instance.getWorldSettings().get(wname));// cancel
						instance.getMessaging().sendMessage(sender, wname, "", false);// print result
					} else {// no such world
						instance.getMessaging().sendError(sender, ErrorType.WORLD_DNE, split[i]);
					}
				}
			}
		} else {
			instance.getMessaging().sendError(sender, ErrorType.STOP_PERM, ""); // send no stop permissions error
		}
	}
	// --------------------------- loop command helper ----------------------------
	private void startShift(Player player, CommandSender sender, boolean isPlayer, String[] split) {
		if (!isPlayer || checkShift(player)) {
			LoopSetting loop = instance.getLoopSettings().get(instance.getCommandAliases().get(split[0].toLowerCase()));// we'll use this in the common cases... declare it once.
			if (split.length == 1) {
				if (isPlayer) { // player world start
					World w = player.getWorld();
					setShift(w, loop, sender);// start or change shift
				} else {// can't do console without a world
					instance.getMessaging().sendError(sender, ErrorType.CONSOLE_WORLD_NEEDED, "");
				}
			} else {// multi-world loop
				for (int i = 1; i < split.length; i++) { // for each world listed
					World w = instance.getServer().getWorld(split[i]);// try to get a world for each worldname
					if (w != null) { // if a world by that name exists
						setShift(w, loop, sender);// start the loop
					} else {// unless it doesn't exist
						instance.getMessaging().sendError(sender, ErrorType.WORLD_DNE, split[i]);
					}
				}
			}
		} else {// player without perms
			instance.getMessaging().sendError(sender, ErrorType.SHIFT_PERM, ""); // send no shift permissions error }
		}
	}

	// ------------------- Permissions -----------------------
	// check if a player has permission to use shift commands (timeshift.shift)
	private boolean checkShift(Player player) {
		return player.hasPermission(time + cmd + "." + cmd);
	}
	// check if a player has permission to use startup commands (timeshift.startup)
	private boolean checkStartup(Player player) {
		return player.hasPermission(time + cmd + ".startup");
	}
	//check if a player has timeshift.cancel
	private boolean checkStop(Player player) {
		return player.hasPermission(time + cmd + ".cancel");
	}

	// this function parses shift commands and handles the different arguments possible for setting current behavior or startup behavior
	@Override
	public boolean onCommand(CommandSender sender, Command command, String commandLabel, String[] split) throws NullPointerException, ArrayIndexOutOfBoundsException {
		// get basic info needed
		Player player;
		Boolean isPlayer;
		if (sender instanceof Player) {// If it's a player, we respond differently than if it's the console.
			player = (Player) sender;// Cast it when we can
			isPlayer = true;
		} else {
			player = null;
			isPlayer = false;
		}
		// No arguments supplied to command, lets help them out a bit
		if (split.length == 0) {
			if (isPlayer) {
				sendHelp(player);
			} else { // not a player (console)
				sendHelp(sender);
			}// command handled
			return true;
		}// length was at least 1.

		// Lets see what they want to do...
		String cmd = instance.getCommandAliases().get(split[0].toLowerCase());
		if (cmd == null) {// no command found!
			if (isPlayer) {
				sendHelp(player);
			} else {
				sendHelp(sender);
			}
			return true;
		}

		if ("TS STARTUP".equals(cmd)) {// Startup Command
			setPersist(player, sender, isPlayer, split);
		} else if ("TS STOP".equals(cmd)) {// is it a stop command?
			stopShift(player, sender, isPlayer, split);
		} else if ("TS ERR".equals(cmd)) {// We got as far as trying to read it... just couldn't quite do it.
			sender.sendMessage("There was an error parsing the command '" + split[0] + "' but someone tried to define it. The server's server.log file will have more information at startup.");
		} else {// Not a stop command, therefore it's a loop command
			startShift(player, sender, isPlayer, split);
		}
		// escaped the if! All success and error cases handled.
		return true;
	}// end of onCommand
}
