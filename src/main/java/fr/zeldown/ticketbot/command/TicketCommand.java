package fr.zeldown.ticketbot.command;

import java.util.concurrent.CompletableFuture;

import fr.zeldown.ticketbot.TicketBot;
import fr.zeldown.ticketbot.config.ScopeConfig;
import fr.zeldown.ticketbot.config.TypeConfig;
import fr.zeldown.ticketbot.message.Placeholders;
import fr.zeldown.ticketbot.message.TicketMessage;
import fr.zeldown.ticketbot.ticket.Ticket;
import fr.zeldown.ticketbot.ticket.TicketException;
import fr.zeldown.ticketbot.ticket.TicketService;
import lombok.NonNull;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

public abstract class TicketCommand implements SlashCommand {

	@Override
	public final void execute(final @NonNull SlashCommandInteractionEvent event) {
		final TypeConfig type = TicketBot.inst().getTickets().type(event.getChannel());
		final ScopeConfig config = type == null ? TicketBot.inst().getConfig().global() : type;
		event.deferReply(true).queue(hook -> CompletableFuture.completedFuture(type).thenCompose(value -> this.process(event, value)).whenComplete((message, error) -> TicketBot.inst().getMessages().respond(hook, config, error)));
	}

	protected @NonNull Member target(final @NonNull SlashCommandInteractionEvent event) {
		final Member target = event.getOption("user", OptionMapping::getAsMember);
		if (target == null) {
			throw new TicketException(TicketMessage.NOT_MEMBER, Placeholders.create().user("user", event.getOption("user", OptionMapping::getAsUser).getIdLong()));
		}
		return target;
	}

	protected abstract @NonNull CompletableFuture<Message> run(final @NonNull SlashCommandInteractionEvent event, final @NonNull Ticket ticket);

	private @NonNull CompletableFuture<Message> process(final @NonNull SlashCommandInteractionEvent event, final TypeConfig type) {
		final TicketService tickets = TicketBot.inst().getTickets();
		if (type == null) {
			throw new TicketException(TicketMessage.NOT_TICKET);
		}

		if (!tickets.isEnabled(type)) {
			throw new TicketException(TicketMessage.DISABLED);
		}

		if (!tickets.isStaff(type, event.getMember())) {
			throw new TicketException(TicketMessage.NO_PERMISSION);
		}

		final TextChannel channel = event.getChannel().asTextChannel();
		if (!tickets.lock(channel)) {
			throw new TicketException(TicketMessage.BUSY);
		}

		return tickets.resolve(type, channel).thenCompose(ticket -> this.run(event, ticket)).whenComplete((message, error) -> tickets.unlock(channel));
	}

}