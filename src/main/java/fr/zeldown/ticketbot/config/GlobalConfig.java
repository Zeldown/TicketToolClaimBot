package fr.zeldown.ticketbot.config;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

@Getter
@Setter
public final class GlobalConfig extends ScopeConfig {

	private long        guild;
	private String      token = "";
	private StatsConfig stats = new StatsConfig();

	@Override
	public @NonNull String name() {
		return "global";
	}

}