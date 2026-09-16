package fr.zeldown.ticketbot.listener;

import java.util.concurrent.TimeUnit;

import fr.zeldown.ticketbot.TicketBot;
import fr.zeldown.ticketbot.config.TypeConfig;
import fr.zeldown.ticketbot.message.Placeholders;
import fr.zeldown.ticketbot.message.TicketMessage;
import fr.zeldown.ticketbot.ticket.TicketService;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.ErrorResponse;

public final class TicketMessageListener extends ListenerAdapter {

	private static final int          DELETE_DELAY = 2;
	private static final int          WARNING_DELAY = 10;
	private static final ErrorHandler IGNORED = new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MESSAGE, ErrorResponse.UNKNOWN_CHANNEL);

	@Override
	public void onMessageReceived(final MessageReceivedEvent event) {
		final Member member = event.getMember();
		final TicketService tickets = TicketBot.inst().getTickets();
		final TypeConfig type = event.isFromGuild() ? tickets.type(event.getChannel()) : null;
		if (type == null || member == null || event.getAuthor().isBot() || !tickets.isEnabled(type) || !tickets.isStaff(type, member) || !tickets.isWaiting(event.getChannel().asTextChannel())) {
			return;
		}

		event.getMessage().delete().queueAfter(TicketMessageListener.DELETE_DELAY, TimeUnit.SECONDS, null, TicketMessageListener.IGNORED);
		event.getChannel().sendMessageEmbeds(TicketBot.inst().getMessages().embed(type, TicketMessage.CLAIM_REQUIRED, Placeholders.create().member("staff", member, tickets.rank(type, member)))).queue(warning -> warning.delete().queueAfter(TicketMessageListener.WARNING_DELAY, TimeUnit.SECONDS, null, TicketMessageListener.IGNORED), TicketMessageListener.IGNORED);
	}

}