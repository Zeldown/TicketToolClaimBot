package fr.zeldown.ticketbot.command;

import lombok.NonNull;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public interface SlashCommand {

	public @NonNull SlashCommandData data();

	public void execute(final @NonNull SlashCommandInteractionEvent event);

	public default void modal(final @NonNull ModalInteractionEvent event) {}

	public default void complete(final @NonNull CommandAutoCompleteInteractionEvent event) {}

}