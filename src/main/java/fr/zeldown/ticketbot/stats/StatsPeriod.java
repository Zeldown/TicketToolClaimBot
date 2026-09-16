package fr.zeldown.ticketbot.stats;

import java.util.concurrent.TimeUnit;

import lombok.Getter;
import lombok.NonNull;

@Getter
public enum StatsPeriod {

	WEEK("7j", "7 derniers jours", 7),
	MONTH("30j", "30 derniers jours", 30),
	QUARTER("90j", "90 derniers jours", 90),
	ALL("all", "Tout l'historique", 0);

	private final int    days;
	private final String key;
	private final String label;

	private StatsPeriod(final String key, final String label, final int days) {
		this.key = key;
		this.label = label;
		this.days = days;
	}

	public long since() {
		return this.days == 0 ? 0L : System.currentTimeMillis() - TimeUnit.DAYS.toMillis(this.days);
	}

	public long previous() {
		return this.days == 0 ? 0L : System.currentTimeMillis() - TimeUnit.DAYS.toMillis(this.days * 2L);
	}

	public static @NonNull StatsPeriod of(final @NonNull String key) {
		for (final StatsPeriod period : StatsPeriod.values()) {
			if (period.key.equals(key)) {
				return period;
			}
		}
		return StatsPeriod.MONTH;
	}

}