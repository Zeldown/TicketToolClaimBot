package fr.zeldown.ticketbot.ticket;

import fr.zeldown.ticketbot.message.Placeholders;
import fr.zeldown.ticketbot.message.TicketMessage;
import lombok.Getter;
import lombok.NonNull;

@Getter
public final class TicketException extends RuntimeException {

	private final TicketMessage template;
	private final Placeholders  placeholders;

	public TicketException(final @NonNull TicketMessage template) {
		this(template, Placeholders.create());
	}

	public TicketException(final @NonNull TicketMessage template, final @NonNull Placeholders placeholders) {
		super(template.getKey(), null, false, false);
		this.template = template;
		this.placeholders = placeholders;
	}

}