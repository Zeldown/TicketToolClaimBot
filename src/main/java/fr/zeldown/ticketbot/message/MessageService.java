package fr.zeldown.ticketbot.message;

import java.awt.Color;
import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import fr.zeldown.ticketbot.TicketBot;
import fr.zeldown.ticketbot.config.MessageTemplate;
import fr.zeldown.ticketbot.config.ScopeConfig;
import fr.zeldown.ticketbot.ticket.Ticket;
import fr.zeldown.ticketbot.ticket.TicketException;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;

@Slf4j
public final class MessageService {

	private static final String OWNER_URL = "https://discord.com/users/";

	public long owner(final @NonNull Message message) {
		if (message.getAuthor().getIdLong() != message.getJDA().getSelfUser().getIdLong() || message.getEmbeds().isEmpty()) {
			return 0L;
		}

		final MessageEmbed.AuthorInfo author = message.getEmbeds().get(0).getAuthor();
		return author == null || author.getUrl() == null || !author.getUrl().startsWith(MessageService.OWNER_URL) ? 0L : Long.parseLong(author.getUrl().substring(MessageService.OWNER_URL.length()));
	}

	public @NonNull String emoji(final @NonNull ScopeConfig config) {
		final RichCustomEmoji emoji = TicketBot.inst().getJda().getEmojiById(config.getEmoji());
		if (emoji != null) {
			return emoji.getFormatted();
		}
		return config.getEmoji() == 0L ? "" : Emoji.fromCustom("emoji", config.getEmoji(), false).getFormatted();
	}

	public void respond(final @NonNull InteractionHook hook, final @NonNull ScopeConfig config, final Throwable error) {
		if (error == null) {
			hook.deleteOriginal().queue();
			return;
		}

		hook.editOriginalEmbeds(this.error(config, error)).queue();
	}

	public @NonNull MessageEmbed embed(final @NonNull ScopeConfig config, final @NonNull TicketMessage message, final @NonNull Placeholders placeholders) {
		return this.builder(config, config.template(message), placeholders).build();
	}

	public @NonNull CompletableFuture<Message> announce(final @NonNull Ticket ticket, final @NonNull TicketMessage message, final @NonNull Placeholders placeholders) {
		final User owner = ticket.getOwner();
		final TextChannel channel = ticket.getChannel();
		final MessageTemplate template = ticket.getType().template(message);
		final EmbedBuilder embed = this.builder(ticket.getType(), template, placeholders.user("owner", owner.getIdLong())).setAuthor(owner.getEffectiveName(), MessageService.OWNER_URL + owner.getId(), owner.getEffectiveAvatarUrl()).setTimestamp(Instant.now());
		final String ping = placeholders.apply(template.getPing()).trim();
		return channel.sendMessageEmbeds(embed.build()).submit().thenCompose(sent -> ping.isEmpty() ? CompletableFuture.completedFuture(sent) : channel.sendMessage(new MessageCreateBuilder().setContent(ping).setAllowedMentions(Collections.emptySet()).mentionUsers(placeholders.getUsers()).mentionRoles(placeholders.getRoles()).build()).flatMap(Message::delete).submit().thenApply(result -> sent));
	}

	private @NonNull MessageEmbed error(final @NonNull ScopeConfig config, final @NonNull Throwable error) {
		final Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
		if (cause instanceof TicketException) {
			final TicketException exception = (TicketException) cause;
			return this.embed(config, exception.getTemplate(), exception.getPlaceholders());
		}

		log.error("Unable to process a ticket action", cause);
		return this.embed(config, TicketMessage.FAILURE, Placeholders.create());
	}

	private @NonNull EmbedBuilder builder(final @NonNull ScopeConfig config, final @NonNull MessageTemplate template, final @NonNull Placeholders placeholders) {
		final String title = placeholders.text("emoji", this.emoji(config)).apply(template.getTitle()).trim();
		return new EmbedBuilder().setTitle(title.isEmpty() ? null : title).setDescription(placeholders.apply(template.getDescription()).trim()).setColor(Color.decode(template.getColor()));
	}

}