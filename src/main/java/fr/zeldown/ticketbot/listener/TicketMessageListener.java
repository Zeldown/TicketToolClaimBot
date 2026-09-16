package fr.zeldown.ticketbot.listener;

import java.util.concurrent.TimeUnit;

import fr.zeldown.ticketbot.TicketBot;
import fr.zeldown.ticketbot.config.TypeConfig;
import fr.zeldown.ticketbot.message.Placeholders;
import fr.zeldown.ticketbot.message.TicketMessage;
import fr.zeldown.ticketbot.ticket.TicketService;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public final class TicketMessageListener extends ListenerAdapter {

	private static final int DELETE_DELAY = 2;
	private static final int WARNING_DELAY = 10;

	@Override
	public void onMessageReceived(final MessageReceivedEvent event) {
		final Member member = event.getMember();
		final TicketService tickets = TicketBot.inst().getTickets();
		final TypeConfig type = event.isFromGuild() ? tickets.type(event.getChannel()) : null;
		if (type == null || member == null || event.getAuthor().isBot() || !tickets.isEnabled(type) || !tickets.isStaff(type, member) || !tickets.isWaiting(event.getChannel().asTextChannel())) {
			return;
		}

		event.getJDA().getGatewayPool().schedule(() -> this.enforce(type, member, event.getMessage()), TicketMessageListener.DELETE_DELAY, TimeUnit.SECONDS);
	}

	private void enforce(final TypeConfig type, final Member member, final Message message) {
		final TicketService tickets = TicketBot.inst().getTickets();
		final TextChannel channel = message.getChannel().asTextChannel();
		message.delete().queue(null, error -> {});
		if (!tickets.isWaiting(channel)) {
			return;
		}

		channel.sendMessageEmbeds(TicketBot.inst().getMessages().embed(type, TicketMessage.CLAIM_REQUIRED, Placeholders.create().member("staff", member, tickets.rank(type, member)))).queue(warning -> warning.delete().queueAfter(TicketMessageListener.WARNING_DELAY, TimeUnit.SECONDS));
	}

}