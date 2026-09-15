package fr.zeldown.ticketbot.message;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Placeholders {

	private static final Pattern PATTERN = Pattern.compile("\\{([a-z]+)\\}");

	@Getter
	private final Set<String>         users = new LinkedHashSet<>();
	@Getter
	private final Set<String>         roles = new LinkedHashSet<>();
	private final Map<String, String> values = new HashMap<>();

	public static @NonNull Placeholders create() {
		return new Placeholders();
	}

	public @NonNull String apply(final String text) {
		if (text == null) {
			return "";
		}

		final Matcher matcher = Placeholders.PATTERN.matcher(text);
		final StringBuffer buffer = new StringBuffer();
		while (matcher.find()) {
			final String value = this.values.get(matcher.group(1));
			matcher.appendReplacement(buffer, Matcher.quoteReplacement(value == null ? matcher.group() : value));
		}
		matcher.appendTail(buffer);
		return buffer.toString();
	}

	public @NonNull Placeholders user(final @NonNull String key, final long id) {
		this.users.add(Long.toString(id));
		return this.text(key, "<@" + id + ">");
	}

	public @NonNull Placeholders text(final @NonNull String key, final @NonNull String value) {
		this.values.put(key, value);
		return this;
	}

	public @NonNull Placeholders roles(final @NonNull String key, final @NonNull Collection<Long> ids) {
		for (final long id : ids) {
			this.roles.add(Long.toString(id));
		}
		return this.text(key, ids.stream().map(id -> "<@&" + id + ">").collect(Collectors.joining(" ")));
	}

	public @NonNull Placeholders member(final @NonNull String key, final @NonNull Member member, final Role rank) {
		this.users.add(member.getId());
		return this.text(key, rank == null ? member.getAsMention() : rank.getAsMention() + " " + member.getAsMention());
	}

}