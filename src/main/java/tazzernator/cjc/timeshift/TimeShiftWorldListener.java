package tazzernator.cjc.timeshift;

import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import tazzernator.cjc.timeshift.settings.WorldSetting;

public class TimeShiftWorldListener implements Listener {

	private TimeShift plugin;

	TimeShiftWorldListener(TimeShift plugin) {
		this.plugin = plugin;
	}

	//check for stored settings for a world being loaded and run them if they exist.
	@EventHandler(priority = EventPriority.LOW)
	@SuppressWarnings("unused")
	public void onWorldLoad(WorldLoadEvent event) {
		World w = event.getWorld();
		String wname = w.getName();
		WorldSetting setting = plugin.getWorldSettings().get(wname);
		if (setting != null && !setting.getLoopName().equals("")) {// if the value isn't "off"
			plugin.scheduleTimer(w, wname, setting); // start a timer / scheduleTimer adds to timers
		}
	}

	//check for active setting + timer on world and cancel it if the world unloads.
	@EventHandler(priority = EventPriority.LOW)
	@SuppressWarnings("unused")
	public void onWorldUnload(WorldUnloadEvent event) {
		World w = event.getWorld();
		String wname = w.getName();
		WorldSetting setting = plugin.getWorldSettings().get(wname);
		if (setting != null && setting.getTid() != -1) {
			plugin.cancelShift(setting);
		}
	}
}
