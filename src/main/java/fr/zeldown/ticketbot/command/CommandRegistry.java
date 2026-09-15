package fr.zeldown.ticketbot.command;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

@Slf4j
public final class CommandRegistry extends ListenerAdapter {

	private final Map<String, SlashCommand> commands = new LinkedHashMap<>();

	public void update(final @NonNull Guild guild) {
		guild.updateCommands().addCommands(this.commands.values().stream().map(SlashCommand::data).collect(Collectors.toList())).queue(registered -> log.info("Registered {} commands on {}", registered.size(), guild.getName()), error -> log.error("Unable to register commands on {}", guild.getName(), error));
	}

	public void register(final @NonNull SlashCommand... commands) {
		for (final SlashCommand command : commands) {
			this.commands.put(command.data().getName(), command);
		}
	}

	@Override
	public void onModalInteraction(final ModalInteractionEvent event) {
		final SlashCommand command = this.commands.get(event.getModalId().split(":", 2)[0]);
		if (command != null) {
			command.modal(event);
		}
	}

	@Override
	public void onSlashCommandInteraction(final SlashCommandInteractionEvent event) {
		final SlashCommand command = this.commands.get(event.getName());
		if (command != null) {
			command.execute(event);
		}
	}

	@Override
	public void onCommandAutoCompleteInteraction(final CommandAutoCompleteInteractionEvent event) {
		final SlashCommand command = this.commands.get(event.getName());
		if (command != null) {
			command.complete(event);
		}
	}

}