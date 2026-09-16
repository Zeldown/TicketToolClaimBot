package fr.zeldown.ticketbot.config;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public final class StatsConfig {

	private long    channel;
	private long    message;
	private int     sla = 120;
	private String  font = "";
	private int     refresh = 5;
	private String  fontBold = "";
	private boolean enabled = true;
	private String  directory = "stats";

}