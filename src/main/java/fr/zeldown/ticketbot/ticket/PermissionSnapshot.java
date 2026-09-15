package fr.zeldown.ticketbot.ticket;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public final class PermissionSnapshot {

	private final long    deny;
	private final long    allow;
	private final boolean role;

}