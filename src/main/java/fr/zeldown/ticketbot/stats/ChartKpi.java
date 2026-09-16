package fr.zeldown.ticketbot.stats;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;

@Getter
@AllArgsConstructor
public final class ChartKpi {

	private final String label;
	private final String value;
	private final String delta;
	private final boolean positive;

	public static @NonNull ChartKpi of(final @NonNull String label, final @NonNull String value) {
		return new ChartKpi(label, value, null, true);
	}

}