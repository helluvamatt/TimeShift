package tazzernator.cjc.timeshift.settings;

public class LoopSetting {

	// The array of settings also defined in config
	private int[][] settings;

	// key, loop name, defined as command in config
	private String loopName;

	public LoopSetting(String l, int[][] s) {
		settings = s;
		loopName = l;
	}

	public String getLoopName() {
		return loopName;
	}

	public int getStartTime(int idx) {
		return settings[idx][0];
	}

	public int getStopTime(int idx) {
		return settings[idx][1];
	}

	// Provides the index of the next setting in the loop.
	public int getNextIdx(int idx) {
		if (idx == settings.length - 1) {
			return 0;
		}
		return ++idx;
	}
}
