package tazzernator.cjc.timeshift;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import tazzernator.cjc.timeshift.TimeShiftConfiguration.TimeShiftMessaging;
import tazzernator.cjc.timeshift.settings.WorldSetting;
import tazzernator.cjc.timeshift.settings.LoopSetting;

/**
 * TimeShift for bukkit
 * 
 * @author Tazzernator (Andrew Tajsic), and cjc343
 * 
 */

public class TimeShift extends JavaPlugin {

	// store server settings in key=worldname, value=setting
	private HashMap<String, WorldSetting> worldSettings = new HashMap<>();
	private HashMap<String, String> commandAliases = new HashMap<>();
	private HashMap<String, LoopSetting> loopSettings = new HashMap<>();
	
	private TimeShiftMessaging messaging;
	private TimeShiftConfiguration configuration;
	
    private FileConfiguration startupConfig = null;
    private File startupFile = null;
    private String path;

	// onDisable must be implemented in a JavaPlugin: called when TimeShift is disabled by the server
	public void onDisable() {
		// stop all the timers
		getServer().getScheduler().cancelTasks(this);
	}

	// onEnable: called when TimeShift is enabled
	// this class sets up TimeShift's initial configuration and ensures that
	// any first-run activities are completed
	public void onEnable() {
		setupConfigFolder();// makes sure config folder exists, defines folder path.
		setupConfigFile();// makes sure config file exists, copies in default if it doesn't.
		//also initializes startup config
		readSettings();// read startup settings from db, or from file on first use of db version.

		// Register events
		// the preprocess event only controls /time, nothing else. It attempts to use any /time commands to cancel an active shift
		// without disrupting the command used.
		PluginManager pm = getServer().getPluginManager();
		pm.registerEvents(new TimeShiftWorldListener(this), this);
		// register the command executor properly now
		if (configuration.detectTime()) { // only register PlayerListener if user wants it. On by default because it's really nifty and cancels asap for other commands.
			// Register a command preprocess event: runs before commands are processed to detect /time [x] commands.
			pm.registerEvents(new TimeShiftPlayerListener(this), this);
		}

		getCommand("shift").setExecutor(new TimeShiftCommandParser(this));

		// Starts one timer for each world with a configured setting
		scheduleTimers();
	}

	TimeShiftMessaging getMessaging() {
		return messaging;
	}

	Map<String, WorldSetting> getWorldSettings() {
		return worldSettings;
	}

	Map<String, String> getCommandAliases() {
		return commandAliases;
	}

	Map<String, LoopSetting> getLoopSettings() {
		return loopSettings;
	}

	//loads startupConfig from YML
    private void loadStartupConfig() {
        if (startupFile == null) {
        	startupFile = new File(path, "/startup.yml");
        }
        startupConfig = YamlConfiguration.loadConfiguration(startupFile);
    }

	// setup folder for config if it doesn't exist, and define path variable.
	// this method ensures that the proper folder structure exists and creates it if it doesn't
	private void setupConfigFolder() {
		if (this.getDataFolder().exists()) {// check that folder exists
			path = this.getDataFolder().getPath();// set path
		} else {// if it doesn't, make it.
			if (this.getDataFolder().mkdirs()) {
				path = this.getDataFolder().getPath();// set path variable
			} else {
				getLogger().warning(getName() + " could not create necessary folder structure for settings.");
			}
		}
	}

	// setup config file if it doesn't exist.
	// this method checks for the existence of the localization and configuration file
	// it copies in defaults if it doesn't exist.
	private void setupConfigFile() {
		try {// check for config file
			File config = new File(path, "/config.yml");
			if (!config.exists()) {// if it doesn't exist:
				// copy over defaults from the jar.
				InputStream defaultConf = getClass().getResourceAsStream("/config.yml");
				FileWriter confWrite = new FileWriter(config);
				for (int i; (i = defaultConf.read()) > 0;) {
					confWrite.write(i);
				}
				confWrite.flush();
				confWrite.close();
				defaultConf.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		configuration = new TimeShiftConfiguration(this);
		configuration.readLoops();
		messaging = configuration.createMessaging();// set up messaging strings now that config file is loaded
		loadStartupConfig();
	}

	private void readSettings() {
		Set<String> keys = startupConfig.getKeys(false);
		for (String key : keys) {
			String setting = startupConfig.getString(key + ".setting");//type defines what it does
			WorldSetting worldSetting = new WorldSetting();
			worldSetting.setLoopName(setting);
			worldSetting.setWorldName(key);
			worldSetting.setTid(-1);			
			worldSettings.put(key, worldSetting);
		}
	}

	// write new setting to database or modify existing setting
	void persistentWriter(String cmdname, World w) {
		startupConfig.set(w.getName() + ".setting", cmdname);
		try {
			startupConfig.save(startupFile);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// schedules a timer for each world with an active loop setting.
	private void scheduleTimers() {
		for (World w : getServer().getWorlds()) {
			String wname = w.getName();
			WorldSetting setting = worldSettings.get(wname);

			if (setting != null && !"".equals(setting.getLoopName())) {
				LoopSetting loopSetting = loopSettings.get(setting.getLoopName());
				if (loopSetting == null) {
					getLogger().warning("The startup setting for the world '" + wname + "' has been ignored because the setting '" + setting.getLoopName() + "' could not be found in the configuration.");
					continue;
				}
				int start = loopSetting.getStartTime(0);
				w.setTime(start);
				scheduleTimer(w, wname, setting, loopSetting);
			}
		}
	}
	
	//TODO Only change time if it must be for next period, figure out why riseset/setrise is so fucked
	
	//schedule timer of the future!
	void scheduleTimer(World world, LoopSetting loopSetting, int nextIdx, long diff) {
		// schedule a one off timer that reschedules for the next time (using this method).
		final TimeShiftShifter tss = new TimeShiftShifter(this, world, loopSetting, nextIdx, loopSetting.getStopTime(nextIdx), loopSetting.getStartTime(loopSetting.getNextIdx(nextIdx)));//set info for TSS to use
		String wname = world.getName();//get world name and associated setting
		WorldSetting worldSetting = worldSettings.get(wname);
		//cancel if the current task is still queued.
		if (getServer().getScheduler().isQueued(worldSetting.getTid())) {
			getServer().getScheduler().cancelTask(worldSetting.getTid());
		}
		int tid = -1;
		while (tid == -1) {
			tid = getServer().getScheduler().scheduleSyncDelayedTask(this, tss, diff);
		}
		worldSetting.setTid(tid);//(re)scheduled
		worldSettings.put(wname, worldSetting);//save for cancellation purposes (world unload, etc)
	}

	//way of the past? schedule repeating tasks to monitor time
	void scheduleTimer(World w, String wname, WorldSetting worldSetting, LoopSetting loopSetting) {
		worldSettings.put(wname, worldSetting); // save what's been done so far because scheduleTimer will re-retrieve on next call.
		int start = loopSetting.getStartTime(0);
		w.setTime(start);
		scheduleTimer(w, loopSetting, 0, loopSetting.getStopTime(0) - start);
	}

	// Schedule a timer after finding a loop setting
	void scheduleTimer(World w, String wname, WorldSetting setting) {
		scheduleTimer(w, wname, setting, loopSettings.get(setting.getLoopName()));
	}

	// cancels task and removes loop name setting from a WorldSetting. 
	void cancelShift(WorldSetting setting) {
		getServer().getScheduler().cancelTask(setting.getTid());//cancel task
		setting.setTid(-1);//set tid
		setting.setLoopName("");//remove loop name
		worldSettings.put(setting.getWorldName(), setting);//overwrite old setting
	}
	
	//Want an API? and you made it this far... let me know.
//	// A public method for canceling a shift, not used internally.
//	public boolean cancelShift(String wname) {
//		WorldSetting setting = world_settings.get(wname);
//		if (setting != null && setting.getTid() != -1) {
//			cancelShift(setting);
//			return true;
//		}
//		return false;
//	}
//	// A public method for listing the loops, not used internally
//	public String[] getLoopNames() {
//		return loop_settings.keySet().toArray(new String[0]);
//	}
//	// A public method for starting a shift, not used internally.
//	public boolean startShift(String worldName, String loopName) {
//		World w = getServer().getWorld(worldName);
//		worldName = w.getName();
//		LoopSetting l = loop_settings.get(loopName);
//		if (l != null && w != null) {
//			WorldSetting ws = world_settings.get(worldName);
//			if (ws == null) {
//				ws = new WorldSetting();
//				ws.setWorldName(worldName);
//				ws.setLoopName(loopName);
//				ws.setTid(-1);
//			}
//			scheduleTimer(w, worldName, ws, l);
//			return true;
//		}
//		return false;
//	}
}
