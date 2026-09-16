package fr.zeldown.ticketbot.ticket;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import fr.zeldown.ticketbot.config.ConfigService;
import fr.zeldown.ticketbot.config.TicketTeam;
import fr.zeldown.ticketbot.config.TypeConfig;
import fr.zeldown.ticketbot.message.MessageService;
import fr.zeldown.ticketbot.message.Placeholders;
import fr.zeldown.ticketbot.message.TicketMessage;
import fr.zeldown.ticketbot.stats.StatsEvent;
import fr.zeldown.ticketbot.stats.StatsService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.RestAction;

@RequiredArgsConstructor
public final class TicketService {

	private static final int          HISTORY = 100;
	private static final Permission[] ACCESS = { Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY, Permission.MESSAGE_ATTACH_FILES, Permission.MESSAGE_EMBED_LINKS };

	private final StatsService                             stats;
	private final ConfigService                            config;
	private final MessageService                           messages;
	private final Set<Long>                                locks = ConcurrentHashMap.newKeySet();
	private final Map<Long, Map<Long, PermissionSnapshot>> expected = new ConcurrentHashMap<>();

	public void unlock(final @NonNull TextChannel channel) {
		this.locks.remove(channel.getIdLong());
	}

	public TypeConfig type(final @NonNull Channel channel) {
		if (channel.getType() != ChannelType.TEXT || ((TextChannel) channel).getGuild().getIdLong() != this.config.global().getGuild()) {
			return null;
		}

		final TextChannel text = (TextChannel) channel;
		for (final TypeConfig type : this.config.types()) {
			if (type.getCategories().contains(text.getParentCategoryIdLong())) {
				return type;
			}
		}

		for (final TypeConfig type : this.config.types()) {
			if (type.getCloseCategories().contains(text.getParentCategoryIdLong()) && PermissionEditor.find(text, type.getStaffRole()) != null) {
				return type;
			}
		}
		return null;
	}

	public boolean lock(final @NonNull TextChannel channel) {
		return this.locks.add(channel.getIdLong());
	}

	public boolean isEnabled(final @NonNull TypeConfig type) {
		return this.config.global().isEnabled() && type.isEnabled();
	}

	public boolean isWaiting(final @NonNull TextChannel channel) {
		for (final PermissionOverride override : channel.getMemberPermissionOverrides()) {
			if (override.getAllowed().contains(Permission.VIEW_CHANNEL) && override.getDenied().contains(Permission.MESSAGE_SEND)) {
				return true;
			}
		}
		return false;
	}

	public Role rank(final @NonNull TypeConfig type, final @NonNull Member member) {
		return this.find(member, type.getRanks());
	}

	public boolean isStaff(final @NonNull TypeConfig type, final @NonNull Member member) {
		return member.hasPermission(Permission.ADMINISTRATOR) || this.find(member, type.roles()) != null;
	}

	public @NonNull CompletableFuture<List<Long>> humans(final @NonNull TextChannel channel) {
		final List<PermissionOverride> overrides = channel.getMemberPermissionOverrides();
		if (overrides.isEmpty()) {
			return CompletableFuture.completedFuture(Collections.emptyList());
		}

		return RestAction.allOf(overrides.stream().map(override -> channel.getJDA().retrieveUserById(override.getIdLong()).map(user -> user.isBot() ? 0L : user.getIdLong()).onErrorMap(error -> override.getIdLong())).collect(Collectors.toList())).map(ids -> ids.stream().filter(id -> id != 0L).collect(Collectors.toList())).submit();
	}

	public @NonNull List<Command.Choice> teams(final TypeConfig type, final @NonNull String query) {
		if (type == null) {
			return Collections.emptyList();
		}

		final String search = query.toLowerCase(Locale.ROOT);
		return type.getTeams().entrySet().stream().filter(entry -> entry.getKey().contains(search) || entry.getValue().getName().toLowerCase(Locale.ROOT).contains(search)).limit(OptionData.MAX_CHOICES).map(entry -> new Command.Choice(entry.getValue().getName(), entry.getKey())).collect(Collectors.toList());
	}

	public boolean consume(final @NonNull TextChannel channel, final @NonNull PermissionOverride override) {
		final Map<Long, PermissionSnapshot> pending = this.expected.get(channel.getIdLong());
		final PermissionSnapshot snapshot = pending == null ? null : pending.get(override.getIdLong());
		if (snapshot == null || snapshot.getAllow() != override.getAllowedRaw() || snapshot.getDeny() != override.getDeniedRaw()) {
			return false;
		}

		pending.remove(override.getIdLong());
		return true;
	}

	public @NonNull CompletableFuture<Message> claim(final @NonNull Ticket ticket, final @NonNull Member staff) {
		if (!ticket.isWaiting()) {
			throw new TicketException(TicketMessage.ALREADY_CLAIMED);
		}

		final PermissionEditor editor = this.hide(ticket, Collections.emptySet());
		editor.grantMember(ticket.getOwner().getIdLong(), Permission.MESSAGE_SEND).grantMember(staff.getIdLong(), TicketService.ACCESS);
		return this.apply(editor).thenCompose(result -> {
			this.stats.log(this.event(StatsEvent.CLAIM, ticket).staff(staff.getIdLong()));
			return this.messages.announce(ticket, TicketMessage.CLAIM, this.placeholders(ticket, staff));
		});
	}

	public @NonNull CompletableFuture<Ticket> resolve(final @NonNull TypeConfig type, final @NonNull TextChannel channel) {
		return MessageHistory.getHistoryFromBeginning(channel).limit(TicketService.HISTORY).submit().thenCompose(history -> {
			final long owner = history.getRetrievedHistory().stream().mapToLong(this.messages::owner).filter(id -> id != 0L).findFirst().orElseThrow(() -> new TicketException(TicketMessage.UNMANAGED));
			return channel.getJDA().retrieveUserById(owner).submit();
		}).thenApply(owner -> new Ticket(owner, type, channel));
	}

	public @NonNull CompletableFuture<Message> open(final @NonNull TypeConfig type, final @NonNull TextChannel channel, final long owner) {
		final PermissionEditor editor = this.editor(type, channel).denyMember(owner, Permission.MESSAGE_SEND);
		if (this.exists(channel, type.getStaffRole())) {
			editor.grantRole(type.getStaffRole(), TicketService.ACCESS);
		}

		final long delay = TicketQueue.delay(this.stats, type);
		final Placeholders placeholders = Placeholders.create().text("queue", TicketQueue.queue(this.position(type, channel))).text("eta", TicketQueue.eta(delay)).text("notice", TicketQueue.notice(delay)).text("mention", "");
		if (type.getMentionRole() != 0L) {
			placeholders.roles("mention", Collections.singleton(type.getMentionRole()));
		}

		return this.apply(editor).thenCompose(result -> {
			this.stats.log(StatsEvent.create(StatsEvent.OPEN, type, channel).owner(owner));
			return channel.getJDA().retrieveUserById(owner).submit();
		}).thenCompose(user -> this.messages.announce(new Ticket(user, type, channel), TicketMessage.WAITING, placeholders));
	}

	public @NonNull CompletableFuture<Message> add(final @NonNull Ticket ticket, final @NonNull Member staff, final @NonNull Member target) {
		final Placeholders placeholders = this.target(ticket, staff, target);
		if (ticket.override(target.getIdLong()) != null) {
			throw new TicketException(TicketMessage.ALREADY_ADDED, placeholders);
		}

		return this.apply(PermissionEditor.of(ticket.getChannel()).grantMember(target.getIdLong(), TicketService.ACCESS)).thenCompose(result -> {
			this.stats.log(this.event(StatsEvent.ADD, ticket).staff(staff.getIdLong()));
			return this.messages.announce(ticket, TicketMessage.ADD, placeholders);
		});
	}

	public @NonNull CompletableFuture<Message> remove(final @NonNull Ticket ticket, final @NonNull Member staff, final @NonNull Member target) {
		final Placeholders placeholders = this.target(ticket, staff, target);
		if (target.getIdLong() == ticket.getOwner().getIdLong()) {
			throw new TicketException(TicketMessage.REMOVE_OWNER, placeholders);
		}

		if (target.getIdLong() == staff.getIdLong()) {
			throw new TicketException(TicketMessage.REMOVE_SELF, placeholders);
		}

		if (ticket.override(target.getIdLong()) == null) {
			throw new TicketException(TicketMessage.NOT_ADDED, placeholders);
		}

		return this.apply(PermissionEditor.of(ticket.getChannel()).remove(target.getIdLong())).thenCompose(result -> {
			this.stats.log(this.event(StatsEvent.REMOVE, ticket).staff(staff.getIdLong()));
			return this.messages.announce(ticket, TicketMessage.REMOVE, placeholders);
		});
	}

	public @NonNull CompletableFuture<Message> transfer(final @NonNull Ticket ticket, final @NonNull Member staff, final @NonNull String id, final @NonNull String reason) {
		final TicketTeam team = ticket.getType().getTeams().get(id);
		final Set<Long> roles = team == null ? Collections.emptySet() : team.getRoles().stream().filter(role -> this.exists(ticket.getChannel(), role)).collect(Collectors.toCollection(LinkedHashSet::new));
		if (roles.isEmpty()) {
			throw new TicketException(TicketMessage.UNKNOWN_TEAM, Placeholders.create().text("team", id));
		}

		final long owner = ticket.getOwner().getIdLong();
		return this.humans(ticket.getChannel()).thenCompose(members -> {
			final PermissionEditor editor = this.hide(ticket, roles).denyMember(owner, Permission.MESSAGE_SEND);
			for (final long member : members) {
				if (member != owner) {
					editor.remove(member);
				}
			}

			for (final long role : roles) {
				editor.grantRole(role, TicketService.ACCESS);
			}
			return this.apply(editor);
		}).thenCompose(result -> {
			this.stats.log(this.event(StatsEvent.TRANSFER, ticket).staff(staff.getIdLong()).team(id).reason(reason));
			return this.messages.announce(ticket, TicketMessage.TRANSFER, this.placeholders(ticket, staff).text("team", team.getName()).text("reason", reason).roles("roles", roles));
		});
	}

	public @NonNull CompletableFuture<Void> reconcile(final @NonNull TypeConfig type, final @NonNull TextChannel channel, final @NonNull Map<Long, PermissionSnapshot> previous) {
		final Set<Long> roles = type.roles();
		final PermissionEditor editor = PermissionEditor.of(channel);
		final List<Long> members = previous.entrySet().stream().filter(entry -> !entry.getValue().isRole()).map(Map.Entry::getKey).collect(Collectors.toList());
		for (final Map.Entry<Long, PermissionSnapshot> entry : previous.entrySet()) {
			if (entry.getValue().isRole() && roles.contains(entry.getKey())) {
				editor.setRole(entry.getKey(), entry.getValue().getAllow(), entry.getValue().getDeny());
			}
		}

		if (this.closed(channel, previous, members)) {
			this.stats.log(StatsEvent.create(StatsEvent.CLOSE, type, channel));
		}

		if (members.isEmpty()) {
			return this.apply(editor);
		}

		return RestAction.allOf(members.stream().map(id -> channel.getGuild().retrieveMemberById(id).onErrorMap(error -> null)).collect(Collectors.toList())).submit().thenCompose(resolved -> {
			for (int index = 0; index < members.size(); index++) {
				this.guard(type, editor, resolved.get(index), members.get(index), previous.get(members.get(index)));
			}
			return this.apply(editor);
		});
	}

	private boolean exists(final @NonNull TextChannel channel, final long role) {
		return channel.getGuild().getRoleById(role) != null;
	}

	private @NonNull StatsEvent event(final @NonNull String event, final @NonNull Ticket ticket) {
		return StatsEvent.create(event, ticket.getType(), ticket.getChannel()).owner(ticket.getOwner().getIdLong());
	}

	private boolean closed(final @NonNull TextChannel channel, final @NonNull Map<Long, PermissionSnapshot> previous, final @NonNull List<Long> members) {
		for (final long member : members) {
			final PermissionOverride override = PermissionEditor.find(channel, member);
			if ((previous.get(member).getAllow() & Permission.VIEW_CHANNEL.getRawValue()) != 0L && override != null && !override.getAllowed().contains(Permission.VIEW_CHANNEL)) {
				return true;
			}
		}
		return false;
	}

	private Role find(final @NonNull Member member, final @NonNull Set<Long> roles) {
		for (final Role role : member.getRoles()) {
			if (roles.contains(role.getIdLong())) {
				return role;
			}
		}
		return null;
	}

	private @NonNull CompletableFuture<Void> apply(final @NonNull PermissionEditor editor) {
		this.expected.computeIfAbsent(editor.getChannel().getIdLong(), id -> new ConcurrentHashMap<>()).putAll(editor.getChanges());
		return editor.submit();
	}

	private long position(final @NonNull TypeConfig type, final @NonNull TextChannel channel) {
		return channel.getGuild().getTextChannels().stream().filter(other -> other.getIdLong() < channel.getIdLong() && type.getCategories().contains(other.getParentCategoryIdLong()) && this.isWaiting(other)).count();
	}

	private @NonNull PermissionEditor hide(final @NonNull Ticket ticket, final @NonNull Set<Long> kept) {
		final TypeConfig type = ticket.getType();
		final Set<Long> roles = type.roles();
		final PermissionEditor editor = this.editor(type, ticket.getChannel());
		for (final PermissionOverride override : ticket.getChannel().getRolePermissionOverrides()) {
			if (roles.contains(override.getIdLong()) && !type.getSupervisorRoles().contains(override.getIdLong()) && !kept.contains(override.getIdLong())) {
				editor.denyRole(override.getIdLong(), Permission.VIEW_CHANNEL);
			}
		}
		return editor;
	}

	private @NonNull Placeholders placeholders(final @NonNull Ticket ticket, final @NonNull Member staff) {
		return Placeholders.create().member("staff", staff, this.rank(ticket.getType(), staff));
	}

	private @NonNull PermissionEditor editor(final @NonNull TypeConfig type, final @NonNull TextChannel channel) {
		final PermissionEditor editor = PermissionEditor.of(channel);
		for (final long role : type.getSupervisorRoles()) {
			if (this.exists(channel, role)) {
				editor.grantRole(role, TicketService.ACCESS);
			}
		}

		return editor;
	}

	private @NonNull Placeholders target(final @NonNull Ticket ticket, final @NonNull Member staff, final @NonNull Member target) {
		final Placeholders placeholders = this.placeholders(ticket, staff).member("user", target, this.rank(ticket.getType(), target));
		if (target.getUser().isBot()) {
			throw new TicketException(TicketMessage.BOT_TARGET, placeholders);
		}
		return placeholders;
	}

	private void guard(final @NonNull TypeConfig type, final @NonNull PermissionEditor editor, final Member member, final long id, final @NonNull PermissionSnapshot previous) {
		final long send = Permission.MESSAGE_SEND.getRawValue();
		final PermissionOverride override = PermissionEditor.find(editor.getChannel(), id);
		if (member != null && this.isStaff(type, member)) {
			editor.setMember(id, previous.getAllow(), previous.getDeny());
		} else if (override != null) {
			editor.setMember(id, (override.getAllowedRaw() & ~send) | (previous.getAllow() & send), (override.getDeniedRaw() & ~send) | (previous.getDeny() & send));
		}
	}

}