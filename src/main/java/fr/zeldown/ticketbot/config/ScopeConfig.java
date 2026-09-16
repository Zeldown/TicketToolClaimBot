package fr.zeldown.ticketbot.config;

import java.util.LinkedHashMap;
import java.util.Map;

import fr.zeldown.ticketbot.message.TicketMessage;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

@Getter
@Setter
public abstract class ScopeConfig {

	private boolean                      enabled = true;
	private long                         emoji = 1039347338038222989L;
	private Map<String, MessageTemplate> messages = new LinkedHashMap<>();

	public void complete() {
		for (final TicketMessage message : TicketMessage.values()) {
			this.messages.putIfAbsent(message.getKey(), message.template());
		}
	}

	public abstract @NonNull String name();

	public @NonNull MessageTemplate template(final @NonNull TicketMessage message) {
		return this.messages.get(message.getKey());
	}

}