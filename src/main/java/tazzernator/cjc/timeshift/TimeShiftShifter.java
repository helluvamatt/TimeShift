package tazzernator.cjc.timeshift;

import org.bukkit.World;

import tazzernator.cjc.timeshift.settings.LoopSetting;

public class TimeShiftShifter implements Runnable {

	TimeShiftShifter(TimeShift plugin, World world, LoopSetting loopSetting, int currentIdx, int nextStartTime, int stopTime)
	{
		this.plugin = plugin;
		this.world = world;
		this.loopSetting = loopSetting;
		this.currentIdx = currentIdx;
		this.nextStartTime = nextStartTime;
		this.stopTime = stopTime;
	}

	private World world = null;
	private LoopSetting loopSetting;
	private TimeShift plugin;
	private int currentIdx;
	private int nextStartTime;
	private int stopTime;

	public void run() {
		long time = world.getTime();
		if (time < stopTime && !(time < 20 && stopTime == 24000)) {
			plugin.scheduleTimer(world, loopSetting, currentIdx, (stopTime - time));
			return;
		}
		if (!( stopTime == 24000 && nextStartTime == 0)) {
			world.setTime(nextStartTime);//set the time and schedule a new timer for when it should end.
		}
		currentIdx = loopSetting.getNextIdx(currentIdx);//get next index
		plugin.scheduleTimer(world, loopSetting, currentIdx, loopSetting.getStopTime(currentIdx) - loopSetting.getStartTime(currentIdx));
	}
}
