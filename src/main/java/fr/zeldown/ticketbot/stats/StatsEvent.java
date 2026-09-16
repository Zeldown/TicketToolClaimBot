package fr.zeldown.ticketbot.stats;

import fr.zeldown.ticketbot.config.TypeConfig;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

@Getter
@NoArgsConstructor
public final class StatsEvent {

	public static final String ADD = "add";
	public static final String OPEN = "open";
	public static final String CLAIM = "claim";
	public static final String CLOSE = "close";
	public static final String DELETE = "delete";
	public static final String REMOVE = "remove";
	public static final String TRANSFER = "transfer";

	private long   owner;
	private long   staff;
	private String type;
	private String team;
	private long   channel;
	private String event;
	private String reason;
	private long   time = System.currentTimeMillis();

	public static @NonNull StatsEvent create(final @NonNull String event, final @NonNull TypeConfig type, final @NonNull TextChannel channel) {
		final StatsEvent created = new StatsEvent();
		created.event = event;
		created.type = type.getId();
		created.channel = channel.getIdLong();
		return created;
	}

	public @NonNull StatsEvent team(final String team) {
		this.team = team;
		return this;
	}

	public @NonNull StatsEvent owner(final long owner) {
		this.owner = owner;
		return this;
	}

	public @NonNull StatsEvent staff(final long staff) {
		this.staff = staff;
		return this;
	}

	public @NonNull StatsEvent reason(final String reason) {
		this.reason = reason;
		return this;
	}

}