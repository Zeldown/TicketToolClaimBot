package fr.zeldown.ticketbot.listener;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import fr.zeldown.ticketbot.TicketBot;
import fr.zeldown.ticketbot.config.TypeConfig;
import fr.zeldown.ticketbot.stats.StatsEvent;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.guild.override.PermissionOverrideCreateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

@Slf4j
public final class TicketOpenListener extends ListenerAdapter {

	private final Set<Long> pending = ConcurrentHashMap.newKeySet();

	@Override
	public void onChannelCreate(final ChannelCreateEvent event) {
		final TypeConfig type = TicketBot.inst().getTickets().type(event.getChannel());
		if (type == null || !TicketBot.inst().getTickets().isEnabled(type)) {
			return;
		}

		this.pending.add(event.getChannel().getIdLong());
		this.detect(type, event.getChannel().asTextChannel());
	}

	@Override
	public void onChannelDelete(final ChannelDeleteEvent event) {
		final TypeConfig type = TicketBot.inst().getTickets().type(event.getChannel());
		if (type != null) {
			TicketBot.inst().getStats().log(StatsEvent.create(StatsEvent.DELETE, type, event.getChannel().asTextChannel()));
		}
		this.pending.remove(event.getChannel().getIdLong());
	}

	@Override
	public void onPermissionOverrideCreate(final PermissionOverrideCreateEvent event) {
		final TypeConfig type = event.isMemberOverride() && this.pending.contains(event.getChannel().getIdLong()) ? TicketBot.inst().getTickets().type(event.getChannel()) : null;
		if (type != null) {
			this.detect(type, event.getChannel().asTextChannel());
		}
	}

	private void detect(final TypeConfig type, final TextChannel channel) {
		TicketBot.inst().getTickets().humans(channel).thenAccept(owners -> {
			if (!owners.isEmpty() && this.pending.remove(channel.getIdLong())) {
				this.open(type, channel, owners.get(0));
			}
		});
	}

	private void open(final TypeConfig type, final TextChannel channel, final long owner) {
		TicketBot.inst().getTickets().open(type, channel, owner).whenComplete((message, error) -> {
			if (error != null) {
				log.error("Unable to open ticket #{}", channel.getName(), error);
			}
		});
	}

}