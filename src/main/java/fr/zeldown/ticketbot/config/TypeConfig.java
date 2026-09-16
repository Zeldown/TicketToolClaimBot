package fr.zeldown.ticketbot.config;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

@Getter
@Setter
public final class TypeConfig extends ScopeConfig {

	private transient String id;

	private long                    staffRole;
	private long                    mentionRole;
	private Set<Long>               ranks = new LinkedHashSet<>();
	private Set<Long>               categories = new LinkedHashSet<>();
	private Set<Long>               supervisorRoles = new LinkedHashSet<>();
	private Set<Long>               closeCategories = new LinkedHashSet<>();
	private Map<String, TicketTeam> teams = new LinkedHashMap<>();

	@Override
	public @NonNull String name() {
		return this.id;
	}

	public @NonNull Set<Long> roles() {
		final Set<Long> roles = new LinkedHashSet<>(this.supervisorRoles);
		for (final TicketTeam team : this.teams.values()) {
			roles.addAll(team.getRoles());
		}

		if (this.staffRole != 0L) {
			roles.add(this.staffRole);
		}
		return roles;
	}

}