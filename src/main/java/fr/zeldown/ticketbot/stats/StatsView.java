package fr.zeldown.ticketbot.stats;

import lombok.Getter;
import lombok.NonNull;

@Getter
public enum StatsView {

	OVERVIEW("overview", "Vue globale", "📊"),
	STAFF("staff", "Par staff", "👥"),
	PROFILE("profile", "Fiche staff", "👤"),
	TEAMS("teams", "Par équipe", "🔀"),
	HOURS("hours", "Par heure", "🕒"),
	QUALITY("quality", "Qualité de service", "🎯");

	private final String key;
	private final String icon;
	private final String label;

	private StatsView(final String key, final String label, final String icon) {
		this.key = key;
		this.icon = icon;
		this.label = label;
	}

	public static @NonNull StatsView of(final @NonNull String key) {
		for (final StatsView view : StatsView.values()) {
			if (view.key.equals(key)) {
				return view;
			}
		}
		return StatsView.OVERVIEW;
	}

}