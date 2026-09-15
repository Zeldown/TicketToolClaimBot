package fr.zeldown.ticketbot.command.impl;

import java.util.concurrent.CompletableFuture;

import fr.zeldown.ticketbot.TicketBot;
import fr.zeldown.ticketbot.command.TicketCommand;
import fr.zeldown.ticketbot.ticket.Ticket;
import lombok.NonNull;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public final class TicketTransferCommand extends TicketCommand {

	@Override
	public @NonNull SlashCommandData data() {
		return Commands.slash("ticket-transfer", "Transférer ce ticket vers une autre équipe").addOptions(new OptionData(OptionType.STRING, "team", "Équipe de destination", true, true), new OptionData(OptionType.STRING, "reason", "Raison du transfert", true).setMaxLength(1000));
	}

	@Override
	public void complete(final @NonNull CommandAutoCompleteInteractionEvent event) {
		event.replyChoices(TicketBot.inst().getTickets().teams(TicketBot.inst().getTickets().type(event.getChannel()), event.getFocusedOption().getValue())).queue();
	}

	@Override
	protected @NonNull CompletableFuture<Message> run(final @NonNull SlashCommandInteractionEvent event, final @NonNull Ticket ticket) {
		return TicketBot.inst().getTickets().transfer(ticket, event.getMember(), event.getOption("team", OptionMapping::getAsString), event.getOption("reason", OptionMapping::getAsString));
	}

}