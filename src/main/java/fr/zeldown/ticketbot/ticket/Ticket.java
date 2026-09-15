package fr.zeldown.ticketbot.ticket;

import fr.zeldown.ticketbot.config.TypeConfig;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

@Getter
@RequiredArgsConstructor
public final class Ticket {

	private final User        owner;
	private final TypeConfig  type;
	private final TextChannel channel;

	public boolean isWaiting() {
		final PermissionOverride override = this.override(this.owner.getIdLong());
		return override != null && override.getDenied().contains(Permission.MESSAGE_SEND);
	}

	public PermissionOverride override(final long id) {
		return PermissionEditor.find(this.channel, id);
	}

}