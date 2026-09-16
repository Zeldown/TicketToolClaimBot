package fr.zeldown.ticketbot.config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public final class MessageTemplate {

	private String ping;
	private String color;
	private String title;
	private String description;

}