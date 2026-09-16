package fr.zeldown.ticketbot.stats;

import lombok.NonNull;

public final class StatsFormat {

	private StatsFormat() {}

	public static @NonNull String duration(final long millis) {
		if (millis <= 0L) {
			return "—";
		}

		final long seconds = millis / 1000L;
		if (seconds < 60L) {
			return seconds + " s";
		}

		if (seconds < 3600L) {
			return seconds / 60L + " min";
		}
		return seconds / 3600L + " h " + String.format("%02d", seconds % 3600L / 60L);
	}

}