package fr.zeldown.ticketbot.command.impl;

import java.util.concurrent.CompletableFuture;

import fr.zeldown.ticketbot.TicketBot;
import fr.zeldown.ticketbot.command.TicketCommand;
import fr.zeldown.ticketbot.ticket.Ticket;
import lombok.NonNull;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public final class TicketAddCommand extends TicketCommand {

	@Override
	public @NonNull SlashCommandData data() {
		return Commands.slash("ticket-add", "Ajouter un membre à ce ticket").addOption(OptionType.USER, "user", "Membre à ajouter", true);
	}

	@Override
	protected @NonNull CompletableFuture<Message> run(final @NonNull SlashCommandInteractionEvent event, final @NonNull Ticket ticket) {
		return TicketBot.inst().getTickets().add(ticket, event.getMember(), this.target(event));
	}

}