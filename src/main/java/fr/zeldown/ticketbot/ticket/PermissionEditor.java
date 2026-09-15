package fr.zeldown.ticketbot.ticket;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.channel.attribute.IPermissionContainer;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.managers.channel.concrete.TextChannelManager;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class PermissionEditor {

	@Getter
	private final TextChannel                   channel;
	private final Set<Long>                     removals = new LinkedHashSet<>();
	@Getter
	private final Map<Long, PermissionSnapshot> changes = new LinkedHashMap<>();

	public static @NonNull PermissionEditor of(final @NonNull TextChannel channel) {
		return new PermissionEditor(channel);
	}

	public @NonNull CompletableFuture<Void> submit() {
		if (this.changes.isEmpty() && this.removals.isEmpty()) {
			return CompletableFuture.completedFuture(null);
		}

		final TextChannelManager manager = this.channel.getManager();
		for (final Map.Entry<Long, PermissionSnapshot> entry : this.changes.entrySet()) {
			if (entry.getValue().isRole()) {
				manager.putRolePermissionOverride(entry.getKey(), entry.getValue().getAllow(), entry.getValue().getDeny());
			} else {
				manager.putMemberPermissionOverride(entry.getKey(), entry.getValue().getAllow(), entry.getValue().getDeny());
			}
		}

		for (final long id : this.removals) {
			manager.removePermissionOverride(id);
		}
		return manager.submit();
	}

	public @NonNull PermissionEditor remove(final long id) {
		this.changes.remove(id);
		this.removals.add(id);
		return this;
	}

	public @NonNull PermissionEditor setRole(final long id, final long allow, final long deny) {
		return this.set(id, true, allow, deny);
	}

	public @NonNull PermissionEditor setMember(final long id, final long allow, final long deny) {
		return this.set(id, false, allow, deny);
	}

	public @NonNull PermissionEditor denyRole(final long id, final @NonNull Permission... permissions) {
		return this.edit(id, true, 0L, Permission.getRaw(permissions));
	}

	public @NonNull PermissionEditor grantRole(final long id, final @NonNull Permission... permissions) {
		return this.edit(id, true, Permission.getRaw(permissions), 0L);
	}

	public @NonNull PermissionEditor denyMember(final long id, final @NonNull Permission... permissions) {
		return this.edit(id, false, 0L, Permission.getRaw(permissions));
	}

	public @NonNull PermissionEditor grantMember(final long id, final @NonNull Permission... permissions) {
		return this.edit(id, false, Permission.getRaw(permissions), 0L);
	}

	private @NonNull PermissionEditor set(final long id, final boolean role, final long allow, final long deny) {
		final PermissionOverride override = PermissionEditor.find(this.channel, id);
		this.removals.remove(id);
		if (override == null ? allow == 0L && deny == 0L : override.getAllowedRaw() == allow && override.getDeniedRaw() == deny) {
			this.changes.remove(id);
			return this;
		}

		this.changes.put(id, new PermissionSnapshot(deny, allow, role));
		return this;
	}

	private @NonNull PermissionEditor edit(final long id, final boolean role, final long allow, final long deny) {
		final PermissionOverride override = PermissionEditor.find(this.channel, id);
		final PermissionSnapshot current = this.changes.getOrDefault(id, override == null ? new PermissionSnapshot(0L, 0L, role) : new PermissionSnapshot(override.getDeniedRaw(), override.getAllowedRaw(), role));
		return this.set(id, role, (current.getAllow() | allow) & ~deny, (current.getDeny() | deny) & ~allow);
	}

	public static PermissionOverride find(final @NonNull IPermissionContainer channel, final long id) {
		for (final PermissionOverride override : channel.getPermissionOverrides()) {
			if (override.getIdLong() == id) {
				return override;
			}
		}
		return null;
	}

}