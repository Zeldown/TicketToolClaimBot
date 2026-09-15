package fr.zeldown.ticketbot.listener;

import java.util.concurrent.TimeUnit;

import fr.zeldown.ticketbot.TicketBot;
import fr.zeldown.ticketbot.config.TypeConfig;
import fr.zeldown.ticketbot.message.Placeholders;
import fr.zeldown.ticketbot.message.TicketMessage;
import fr.zeldown.ticketbot.ticket.TicketService;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public final class TicketMessageListener extends ListenerAdapter {

	private static final int WARNING_DELAY = 10;

	@Override
	public void onMessageReceived(final MessageReceivedEvent event) {
		final Member member = event.getMember();
		final TicketService tickets = TicketBot.inst().getTickets();
		final TypeConfig type = event.isFromGuild() ? tickets.type(event.getChannel()) : null;
		if (type == null || member == null || event.getAuthor().isBot() || !tickets.isEnabled(type) || !tickets.isStaff(type, member) || tickets.isBypass(type, member) || !tickets.isWaiting(event.getChannel().asTextChannel())) {
			return;
		}

		event.getMessage().delete().queue();
		event.getChannel().sendMessageEmbeds(TicketBot.inst().getMessages().embed(type, TicketMessage.CLAIM_REQUIRED, Placeholders.create().member("staff", member, tickets.rank(type, member)))).queue(warning -> warning.delete().queueAfter(TicketMessageListener.WARNING_DELAY, TimeUnit.SECONDS));
	}

}