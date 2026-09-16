package fr.zeldown.ticketbot.config;

import java.util.LinkedHashSet;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public final class TicketTeam {

	private String    name;
	private Set<Long> roles = new LinkedHashSet<>();

}