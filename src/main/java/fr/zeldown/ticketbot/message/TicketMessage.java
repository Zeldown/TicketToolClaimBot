package fr.zeldown.ticketbot.message;

import fr.zeldown.ticketbot.config.MessageTemplate;
import lombok.Getter;
import lombok.NonNull;

@Getter
public enum TicketMessage {

	WAITING("waiting", "", "#F1C40F", "Ticket en attente de prise en charge", "{emoji} Merci d'avoir ouvert un ticket !\n\nVotre demande est actuellement **en attente d'assignation**, un membre du staff va la prendre en charge très prochainement.\n\n{queue} {eta}\n\nDès que ce sera fait, vous pourrez **expliquer votre problème** directement dans ce salon. Merci de votre patience !\n\n{notice}"),
	CLAIM("claim", "{owner}", "#2ECC71", "Ticket pris en charge", "{staff} prend désormais en charge votre ticket.\n\nVous pouvez dès à présent **expliquer votre problème** en détail dans ce salon, votre demande sera traitée dans les meilleurs délais."),
	ADD("add", "{user}", "#3498DB", "Membre ajouté", "{staff} a ajouté {user} au ticket.\n\n{user} peut désormais consulter ce salon et participer à la discussion."),
	REMOVE("remove", "", "#E67E22", "Membre retiré", "{staff} a retiré {user} du ticket.\n\n{user} n'a plus accès à ce salon."),
	TRANSFER("transfer", "{roles} {owner}", "#9B59B6", "Ticket transféré", "{staff} a transféré votre ticket vers l'équipe **{team}**.\n\n**Raison :** {reason}\n\nVotre ticket est de nouveau **en attente d'assignation**, un membre de {roles} va le prendre en charge très prochainement. Vous pourrez alors poursuivre votre demande."),
	DISABLED("disabled", "Le système de prise en charge des tickets est actuellement désactivé."),
	NOT_TICKET("not-ticket", "Cette commande ne peut être utilisée que dans un ticket."),
	UNMANAGED("unmanaged", "Ce ticket n'est pas géré par le système de prise en charge."),
	BUSY("busy", "Une action est déjà en cours sur ce ticket, veuillez patienter."),
	NO_PERMISSION("no-permission", "Vous devez faire partie du staff pour utiliser cette commande."),
	ALREADY_CLAIMED("already-claimed", "Ce ticket est déjà pris en charge."),
	CLAIM_REQUIRED("claim-required", "{staff}, ce ticket n'est pas encore pris en charge : utilisez `/ticket-claim` avant d'y écrire."),
	ALREADY_ADDED("already-added", "{user} a déjà accès à ce ticket."),
	NOT_ADDED("not-added", "{user} ne fait pas partie de ce ticket."),
	NOT_MEMBER("not-member", "{user} n'est pas membre du serveur."),
	BOT_TARGET("bot-target", "Cette action ne peut pas cibler un bot."),
	BLACKLISTED("blacklisted", "{user} possède un rôle qui n'est pas autorisé à accéder aux tickets."),
	REMOVE_OWNER("remove-owner", "{user} est l'auteur de ce ticket et ne peut pas en être retiré."),
	REMOVE_SELF("remove-self", "Vous ne pouvez pas vous retirer vous-même du ticket, utilisez plutôt `/ticket-transfer`."),
	UNKNOWN_TEAM("unknown-team", "L'équipe **{team}** n'existe pas ou aucun rôle ne lui est assigné."),
	FAILURE("failure", "Une erreur est survenue, vérifiez les permissions du bot.");

	private final String key;
	private final String ping;
	private final String color;
	private final String title;
	private final String description;

	private TicketMessage(final String key, final String description) {
		this(key, "", "#E74C3C", "", description);
	}

	private TicketMessage(final String key, final String ping, final String color, final String title, final String description) {
		this.key = key;
		this.ping = ping;
		this.color = color;
		this.title = title;
		this.description = description;
	}

	public @NonNull MessageTemplate template() {
		return new MessageTemplate(this.ping, this.color, this.title, this.description);
	}

	public static @NonNull TicketMessage of(final @NonNull String key) {
		for (final TicketMessage message : TicketMessage.values()) {
			if (message.key.equals(key)) {
				return message;
			}
		}
		throw new IllegalArgumentException("Unknown message '" + key + "'");
	}

}