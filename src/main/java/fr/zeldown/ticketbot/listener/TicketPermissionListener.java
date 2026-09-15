package fr.zeldown.ticketbot.listener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import fr.zeldown.ticketbot.TicketBot;
import fr.zeldown.ticketbot.config.TypeConfig;
import fr.zeldown.ticketbot.ticket.PermissionSnapshot;
import fr.zeldown.ticketbot.ticket.TicketService;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.override.PermissionOverrideUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

@Slf4j
public final class TicketPermissionListener extends ListenerAdapter {

	private static final int DELAY = 250;

	private final Map<Long, Map<Long, PermissionSnapshot>> batches = new ConcurrentHashMap<>();

	@Override
	public void onPermissionOverrideUpdate(final PermissionOverrideUpdateEvent event) {
		final TicketService tickets = TicketBot.inst().getTickets();
		final TypeConfig type = tickets.type(event.getChannel());
		if (type == null) {
			return;
		}

		final TextChannel channel = event.getChannel().asTextChannel();
		if (!tickets.isEnabled(type) || tickets.consume(channel, event.getPermissionOverride())) {
			return;
		}

		final Map<Long, PermissionSnapshot> batch = new ConcurrentHashMap<>();
		final Map<Long, PermissionSnapshot> existing = this.batches.putIfAbsent(channel.getIdLong(), batch);
		(existing == null ? batch : existing).putIfAbsent(event.getPermissionOverride().getIdLong(), new PermissionSnapshot(event.getOldDenyRaw(), event.getOldAllowRaw(), event.isRoleOverride()));
		if (existing == null) {
			event.getJDA().getGatewayPool().schedule(() -> this.reconcile(type, channel), TicketPermissionListener.DELAY, TimeUnit.MILLISECONDS);
		}
	}

	private void reconcile(final TypeConfig type, final TextChannel channel) {
		TicketBot.inst().getTickets().reconcile(type, channel, this.batches.remove(channel.getIdLong())).whenComplete((result, error) -> {
			if (error != null) {
				log.error("Unable to reconcile permissions of ticket #{}", channel.getName(), error);
			}
		});
	}

}