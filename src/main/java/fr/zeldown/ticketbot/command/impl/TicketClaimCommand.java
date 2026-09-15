package fr.zeldown.ticketbot.command.impl;

import java.util.concurrent.CompletableFuture;

import fr.zeldown.ticketbot.TicketBot;
import fr.zeldown.ticketbot.command.TicketCommand;
import fr.zeldown.ticketbot.ticket.Ticket;
import lombok.NonNull;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public final class TicketClaimCommand extends TicketCommand {

	@Override
	public @NonNull SlashCommandData data() {
		return Commands.slash("ticket-claim", "Prendre en charge ce ticket");
	}

	@Override
	protected @NonNull CompletableFuture<Message> run(final @NonNull SlashCommandInteractionEvent event, final @NonNull Ticket ticket) {
		return TicketBot.inst().getTickets().claim(ticket, event.getMember());
	}

}