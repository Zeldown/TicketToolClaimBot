package fr.zeldown.ticketbot;

import java.nio.file.Paths;
import java.util.EnumSet;

import fr.zeldown.ticketbot.command.CommandRegistry;
import fr.zeldown.ticketbot.command.impl.TicketAddCommand;
import fr.zeldown.ticketbot.command.impl.TicketAdminCommand;
import fr.zeldown.ticketbot.command.impl.TicketClaimCommand;
import fr.zeldown.ticketbot.command.impl.TicketRemoveCommand;
import fr.zeldown.ticketbot.command.impl.TicketTransferCommand;
import fr.zeldown.ticketbot.config.ConfigService;
import fr.zeldown.ticketbot.listener.TicketMessageListener;
import fr.zeldown.ticketbot.listener.TicketOpenListener;
import fr.zeldown.ticketbot.listener.TicketPermissionListener;
import fr.zeldown.ticketbot.message.MessageService;
import fr.zeldown.ticketbot.ticket.TicketService;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

@Slf4j
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TicketBot {

	private static final TicketBot INSTANCE = new TicketBot();

	private JDA             jda;
	private ConfigService   config;
	private TicketService   tickets;
	private MessageService  messages;
	private CommandRegistry commands;

	public static @NonNull TicketBot inst() {
		return TicketBot.INSTANCE;
	}

	public void shutdown() {
		log.info("Stopping TicketBot...");
		this.jda.shutdown();
	}

	private boolean start() {
		try {
			this.config = new ConfigService(Paths.get("config"));
		} catch (final Exception e) {
			log.error("Unable to load the configuration", e);
			return false;
		}

		if (this.config.global().getToken().isEmpty()) {
			log.error("Missing bot token in config/global.json");
			return false;
		}

		this.messages = new MessageService();
		this.tickets = new TicketService(this.config, this.messages);
		this.commands = new CommandRegistry();
		this.commands.register(new TicketAddCommand(), new TicketClaimCommand(), new TicketAdminCommand(), new TicketRemoveCommand(), new TicketTransferCommand());

		try {
			this.jda = JDABuilder.createLight(this.config.global().getToken(), EnumSet.of(GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_EXPRESSIONS)).enableCache(CacheFlag.EMOJI, CacheFlag.MEMBER_OVERRIDES).addEventListeners(this.commands, new TicketOpenListener(), new TicketMessageListener(), new TicketPermissionListener()).build().awaitReady();
		} catch (final Exception e) {
			log.error("Unable to connect to Discord", e);
			return false;
		}

		this.refresh();
		log.info("TicketBot ready with {} ticket type(s).", this.config.types().size());
		return true;
	}

	public void refresh() {
		final Guild guild = this.jda.getGuildById(this.config.global().getGuild());
		if (guild == null) {
			log.warn("Guild {} not found, commands are not registered", this.config.global().getGuild());
			return;
		}

		this.commands.update(guild);
	}

	public static void main(final String[] args) {
		final TicketBot bot = TicketBot.inst();
		if (!bot.start()) {
			System.exit(1);
		}

		Runtime.getRuntime().addShutdownHook(new Thread(bot::shutdown));
	}

}