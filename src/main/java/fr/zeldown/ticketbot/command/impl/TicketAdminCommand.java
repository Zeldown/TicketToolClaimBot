package fr.zeldown.ticketbot.command.impl;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import fr.zeldown.ticketbot.TicketBot;
import fr.zeldown.ticketbot.command.SlashCommand;
import fr.zeldown.ticketbot.config.MessageTemplate;
import fr.zeldown.ticketbot.config.ScopeConfig;
import fr.zeldown.ticketbot.config.TicketTeam;
import fr.zeldown.ticketbot.config.TypeConfig;
import fr.zeldown.ticketbot.message.Placeholders;
import fr.zeldown.ticketbot.message.TicketMessage;
import fr.zeldown.ticketbot.stats.ChartTheme;
import lombok.NonNull;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.IMentionable;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;
import net.dv8tion.jda.api.modals.Modal;

public final class TicketAdminCommand implements SlashCommand {

	private static final int     ERROR = 0xE74C3C;
	private static final int     SUCCESS = 0x2ECC71;
	private static final String  NAME = "ticket-admin";
	private static final Pattern ID = Pattern.compile("[a-z0-9-]{1,32}");
	private static final Pattern COLOR = Pattern.compile("#[0-9a-fA-F]{6}");
	private static final String  PLACEHOLDERS = "Variables : {owner} {staff} {user} {roles} {team} {reason} {emoji} {mention} {queue} {eta} {notice}";

	private final Map<String, Consumer<SlashCommandInteractionEvent>> handlers = new HashMap<>();

	public TicketAdminCommand() {
		this.handlers.put("staff", this::staff);
		this.handlers.put("emoji", this::emoji);
		this.handlers.put("status", this::status);
		this.handlers.put("reload", this::reload);
		this.handlers.put("stats sla", this::sla);
		this.handlers.put("mention", this::mention);
		this.handlers.put("message", this::message);
		this.handlers.put("stats font", this::font);
		this.handlers.put("stats live", this::live);
		this.handlers.put("type create", this::createType);
		this.handlers.put("team create", this::createTeam);
		this.handlers.put("team delete", this::deleteTeam);
		this.handlers.put("enable", event -> this.toggle(event, true));
		this.handlers.put("disable", event -> this.toggle(event, false));
		this.handlers.put("team add-role", event -> this.editTeam(event, true));
		this.handlers.put("category add", event -> this.editCategory(event, true));
		this.handlers.put("team remove-role", event -> this.editTeam(event, false));
		this.handlers.put("category remove", event -> this.editCategory(event, false));
		this.handlers.put("rank add", event -> this.editSet(event, "role", TypeConfig::getRanks, true, "grades"));
		this.handlers.put("rank remove", event -> this.editSet(event, "role", TypeConfig::getRanks, false, "grades"));
		this.handlers.put("close add", event -> this.editSet(event, "category", TypeConfig::getCloseCategories, true, "catégories fermées"));
		this.handlers.put("supervisor add", event -> this.editSet(event, "role", TypeConfig::getSupervisorRoles, true, "rôles superviseurs"));
		this.handlers.put("close remove", event -> this.editSet(event, "category", TypeConfig::getCloseCategories, false, "catégories fermées"));
		this.handlers.put("supervisor remove", event -> this.editSet(event, "role", TypeConfig::getSupervisorRoles, false, "rôles superviseurs"));
	}

	@Override
	public @NonNull SlashCommandData data() {
		final OptionData role = new OptionData(OptionType.ROLE, "role", "Rôle concerné", true);
		final OptionData team = new OptionData(OptionType.STRING, "team", "Équipe concernée", true, true);
		final OptionData type = new OptionData(OptionType.STRING, "type", "Type de ticket concerné", true, true);
		final OptionData scope = new OptionData(OptionType.STRING, "type", "Type de ticket, configuration globale si vide", false, true);
		final OptionData category = new OptionData(OptionType.CHANNEL, "category", "Catégorie concernée", true).setChannelTypes(ChannelType.CATEGORY);
		return Commands
		.slash(TicketAdminCommand.NAME, "Configurer le système de prise en charge des tickets")
		.setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
		.addSubcommands(
				new SubcommandData("enable", "Activer le système, globalement ou pour un type").addOptions(scope),
				new SubcommandData("disable", "Désactiver le système, globalement ou pour un type").addOptions(scope),
				new SubcommandData("status", "Afficher la configuration globale ou d'un type").addOptions(scope),
				new SubcommandData("reload", "Recharger les fichiers de configuration"),
				new SubcommandData("staff", "Définir le rôle staff d'un type").addOptions(type, role),
				new SubcommandData("mention", "Définir le rôle mentionné à l'ouverture d'un ticket").addOptions(type, role),
				new SubcommandData("emoji", "Définir l'emoji des messages").addOption(OptionType.STRING, "emoji", "Emoji personnalisé ou identifiant", true).addOptions(scope),
				new SubcommandData("message", "Modifier un message").addOptions(new OptionData(OptionType.STRING, "key", "Message à modifier", true).addChoices(Arrays.stream(TicketMessage.values()).map(message -> new Command.Choice(message.getKey(), message.getKey())).collect(Collectors.toList())), scope)
				)
		.addSubcommandGroups(
				new SubcommandGroupData("stats", "Configurer les statistiques").addSubcommands(
						new SubcommandData("live", "Définir le salon du suivi en direct").addOption(OptionType.CHANNEL, "channel", "Salon du suivi", true),
						new SubcommandData("sla", "Définir l'objectif de prise en charge, en minutes").addOption(OptionType.INTEGER, "minutes", "Objectif en minutes", true),
						new SubcommandData("font", "Définir la police des graphiques").addOption(OptionType.STRING, "regular", "Chemin du fichier .ttf normal", true).addOption(OptionType.STRING, "bold", "Chemin du fichier .ttf gras", false)
						),
				new SubcommandGroupData("type", "Gérer les types de ticket").addSubcommands(
						new SubcommandData("create", "Créer un type de ticket").addOption(OptionType.STRING, "id", "Identifiant unique, par exemple bedrock", true)
						),
				new SubcommandGroupData("category", "Gérer les catégories d'un type").addSubcommands(
						new SubcommandData("add", "Ajouter une catégorie à un type").addOptions(type, category),
						new SubcommandData("remove", "Retirer une catégorie d'un type").addOptions(type, category)
						),
				new SubcommandGroupData("close", "Gérer les catégories des tickets fermés d'un type").addSubcommands(
						new SubcommandData("add", "Ajouter une catégorie de tickets fermés").addOptions(type, category),
						new SubcommandData("remove", "Retirer une catégorie de tickets fermés").addOptions(type, category)
						),
				new SubcommandGroupData("rank", "Gérer les grades affichés devant les membres du staff").addSubcommands(
						new SubcommandData("add", "Ajouter un grade affichable").addOptions(type, role),
						new SubcommandData("remove", "Retirer un grade affichable").addOptions(type, role)
						),
				new SubcommandGroupData("supervisor", "Gérer les rôles ayant toujours accès aux tickets d'un type").addSubcommands(
						new SubcommandData("add", "Donner à un rôle l'accès permanent aux tickets").addOptions(type, role),
						new SubcommandData("remove", "Retirer l'accès permanent d'un rôle").addOptions(type, role)
						),
				new SubcommandGroupData("team", "Gérer les équipes de transfert d'un type").addSubcommands(
						new SubcommandData("create", "Créer une équipe de transfert").addOptions(type).addOption(OptionType.STRING, "id", "Identifiant unique, par exemple modo", true).addOption(OptionType.STRING, "name", "Nom affiché, par exemple Modération", true).addOptions(role),
						new SubcommandData("delete", "Supprimer une équipe de transfert").addOptions(type, team),
						new SubcommandData("add-role", "Ajouter un rôle à une équipe").addOptions(type, team, role),
						new SubcommandData("remove-role", "Retirer un rôle d'une équipe").addOptions(type, team, role)
						)
				);
	}

	@Override
	public void modal(final @NonNull ModalInteractionEvent event) {
		final String[] parts = event.getModalId().split(":", 3);
		final ScopeConfig scope = parts[1].isEmpty() ? TicketBot.inst().getConfig().global() : TicketBot.inst().getConfig().type(parts[1]);
		final String color = event.getValue("color").getAsString().trim();
		if (scope == null || !TicketAdminCommand.COLOR.matcher(color).matches()) {
			this.reply(event, TicketAdminCommand.ERROR, scope == null ? "Le type `" + parts[1] + "` n'existe plus." : "La couleur doit être au format hexadécimal, par exemple `#2ECC71`.");
			return;
		}

		final TicketMessage message = TicketMessage.of(parts[2]);
		final MessageTemplate template = scope.template(message);
		template.setColor(color);
		template.setPing(event.getValue("ping").getAsString());
		template.setTitle(event.getValue("title").getAsString());
		template.setDescription(event.getValue("description").getAsString());
		this.save();
		event.replyEmbeds(this.embed(TicketAdminCommand.SUCCESS, "Le message `" + message.getKey() + "` de `" + scope.name() + "` a été mis à jour, voici un aperçu :"), TicketBot.inst().getMessages().embed(scope, message, Placeholders.create())).setEphemeral(true).queue();
	}

	@Override
	public void execute(final @NonNull SlashCommandInteractionEvent event) {
		if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
			this.reply(event, TicketAdminCommand.ERROR, "Vous devez être administrateur pour utiliser cette commande.");
			return;
		}

		this.handlers.get(event.getFullCommandName().substring(TicketAdminCommand.NAME.length() + 1)).accept(event);
	}

	@Override
	public void complete(final @NonNull CommandAutoCompleteInteractionEvent event) {
		final String query = event.getFocusedOption().getValue().toLowerCase(Locale.ROOT);
		if (event.getFocusedOption().getName().equals("team")) {
			event.replyChoices(TicketBot.inst().getTickets().teams(TicketBot.inst().getConfig().type(event.getOption("type", "", OptionMapping::getAsString)), query)).queue();
			return;
		}

		event.replyChoices(TicketBot.inst().getConfig().types().stream().map(TypeConfig::getId).filter(id -> id.contains(query)).limit(OptionData.MAX_CHOICES).map(id -> new Command.Choice(id, id)).collect(Collectors.toList())).queue();
	}

	private void save() {
		TicketBot.inst().getConfig().save();
	}

	private long emojiId(final String value) {
		if (value.matches("\\d{1,19}")) {
			return Long.parseLong(value);
		}

		final Matcher matcher = Message.MentionType.EMOJI.getPattern().matcher(value);
		return matcher.matches() ? Long.parseLong(matcher.group(2)) : 0L;
	}

	private void sla(final SlashCommandInteractionEvent event) {
		final int minutes = Math.max(1, event.getOption("minutes", OptionMapping::getAsInt));
		TicketBot.inst().getConfig().global().getStats().setSla(minutes);
		this.save();
		this.reply(event, TicketAdminCommand.SUCCESS, "L'objectif de prise en charge est désormais de **" + minutes + " min**.");
	}

	private void font(final SlashCommandInteractionEvent event) {
		final String regular = event.getOption("regular", OptionMapping::getAsString).trim();
		final String bold = event.getOption("bold", "", OptionMapping::getAsString).trim();
		TicketBot.inst().getConfig().global().getStats().setFont(regular);
		TicketBot.inst().getConfig().global().getStats().setFontBold(bold);
		this.save();
		ChartTheme.load(regular, bold);
		this.reply(event, TicketAdminCommand.SUCCESS, "La police des graphiques est désormais `" + regular + "`.");
	}

	private void live(final SlashCommandInteractionEvent event) {
		final GuildChannelUnion channel = event.getOption("channel", OptionMapping::getAsChannel);
		TicketBot.inst().getConfig().global().getStats().setChannel(channel.getIdLong());
		TicketBot.inst().getConfig().global().getStats().setMessage(0L);
		this.save();
		this.reply(event, TicketAdminCommand.SUCCESS, "Le suivi en direct sera publié dans " + channel.getAsMention() + ".");
	}

	private void staff(final SlashCommandInteractionEvent event) {
		final TypeConfig type = this.type(event);
		if (type == null) {
			return;
		}

		final Role role = event.getOption("role", OptionMapping::getAsRole);
		type.setStaffRole(role.getIdLong());
		this.save();
		this.reply(event, TicketAdminCommand.SUCCESS, "Le rôle staff de `" + type.getId() + "` est désormais " + role.getAsMention() + ".");
	}

	private void emoji(final SlashCommandInteractionEvent event) {
		final ScopeConfig scope = this.scope(event);
		if (scope == null) {
			return;
		}

		final long id = this.emojiId(event.getOption("emoji", OptionMapping::getAsString).trim());
		if (id == 0L) {
			this.reply(event, TicketAdminCommand.ERROR, "Emoji invalide, utilisez un emoji personnalisé ou son identifiant.");
			return;
		}

		scope.setEmoji(id);
		this.save();
		this.reply(event, TicketAdminCommand.SUCCESS, "L'emoji de `" + scope.name() + "` est désormais " + TicketBot.inst().getMessages().emoji(scope) + ".");
	}

	private void status(final SlashCommandInteractionEvent event) {
		final ScopeConfig scope = this.scope(event);
		if (scope == null) {
			return;
		}

		final EmbedBuilder embed = new EmbedBuilder()
		.setTitle("Configuration " + scope.name())
		.setColor(scope.isEnabled() ? TicketAdminCommand.SUCCESS : TicketAdminCommand.ERROR)
		.addField("État", scope.isEnabled() ? "Activé" : "Désactivé", true)
		.addField("Emoji", TicketAdminCommand.fallback(TicketBot.inst().getMessages().emoji(scope)), true);
		if (scope instanceof TypeConfig) {
			final TypeConfig type = (TypeConfig) scope;
			embed
			.addField("Rôle staff", TicketAdminCommand.fallback(type.getStaffRole() == 0L ? "" : TicketAdminCommand.mentions(Collections.singleton(type.getStaffRole()), "@&")), true)
			.addField("Rôle mentionné", TicketAdminCommand.fallback(type.getMentionRole() == 0L ? "" : TicketAdminCommand.mentions(Collections.singleton(type.getMentionRole()), "@&")), true)
			.addField("Catégories", TicketAdminCommand.fallback(TicketAdminCommand.mentions(type.getCategories(), "#")), false)
			.addField("Catégories fermées", TicketAdminCommand.fallback(TicketAdminCommand.mentions(type.getCloseCategories(), "#")), false)
			.addField("Grades", TicketAdminCommand.fallback(TicketAdminCommand.mentions(type.getRanks(), "@&")), false)
			.addField("Rôles superviseurs", TicketAdminCommand.fallback(TicketAdminCommand.mentions(type.getSupervisorRoles(), "@&")), false)
			.addField("Équipes", TicketAdminCommand.fallback(type.getTeams().entrySet().stream().map(entry -> "`" + entry.getKey() + "` **" + entry.getValue().getName() + "** : " + TicketAdminCommand.mentions(entry.getValue().getRoles(), "@&")).collect(Collectors.joining("\n"))), false);
		} else {
			embed.addField("Types", TicketAdminCommand.fallback(TicketBot.inst().getConfig().types().stream().map(type -> "`" + type.getId() + "` " + (type.isEnabled() ? "Activé" : "Désactivé") + " · " + type.getCategories().size() + " catégorie(s)").collect(Collectors.joining("\n"))), false);
		}
		event.replyEmbeds(embed.build()).setEphemeral(true).queue();
	}

	private void reload(final SlashCommandInteractionEvent event) {
		try {
			TicketBot.inst().getConfig().reload();
		} catch (final RuntimeException e) {
			this.reply(event, TicketAdminCommand.ERROR, "Impossible de recharger la configuration : `" + e.getMessage() + "`");
			return;
		}

		TicketBot.inst().refresh();
		this.reply(event, TicketAdminCommand.SUCCESS, "La configuration a été rechargée (" + TicketBot.inst().getConfig().types().size() + " type(s)).");
	}

	private void mention(final SlashCommandInteractionEvent event) {
		final TypeConfig type = this.type(event);
		if (type == null) {
			return;
		}

		final Role role = event.getOption("role", OptionMapping::getAsRole);
		type.setMentionRole(role.getIdLong());
		this.save();
		this.reply(event, TicketAdminCommand.SUCCESS, "Le rôle mentionné à l'ouverture des tickets `" + type.getId() + "` est désormais " + role.getAsMention() + ".");
	}

	private void message(final SlashCommandInteractionEvent event) {
		final ScopeConfig scope = this.scope(event);
		if (scope == null) {
			return;
		}

		final TicketMessage message = TicketMessage.of(event.getOption("key", OptionMapping::getAsString));
		final MessageTemplate template = scope.template(message);
		event.replyModal(Modal
		.create(TicketAdminCommand.NAME + ":" + event.getOption("type", "", OptionMapping::getAsString) + ":" + message.getKey(), "Message " + message.getKey())
		.addComponents(
				Label.of("Ping", "Mentions envoyées puis supprimées aussitôt pour notifier", this.input("ping", TextInputStyle.SHORT, template.getPing(), false, 2000)),
				Label.of("Titre", this.input("title", TextInputStyle.SHORT, template.getTitle(), false, 256)),
				Label.of("Description", TicketAdminCommand.PLACEHOLDERS, this.input("description", TextInputStyle.PARAGRAPH, template.getDescription(), true, 4000)),
				Label.of("Couleur", "Format hexadécimal, par exemple #2ECC71", this.input("color", TextInputStyle.SHORT, template.getColor(), true, 7))
				)
		.build()).queue();
	}

	private MessageEmbed embed(final int color, final String text) {
		return new EmbedBuilder().setColor(color).setDescription(text).build();
	}

	private TypeConfig type(final SlashCommandInteractionEvent event) {
		final String id = event.getOption("type", OptionMapping::getAsString);
		final TypeConfig type = TicketBot.inst().getConfig().type(id);
		if (type == null) {
			this.reply(event, TicketAdminCommand.ERROR, "Le type `" + id + "` n'existe pas.");
		}
		return type;
	}

	private void createType(final SlashCommandInteractionEvent event) {
		final String id = event.getOption("id", OptionMapping::getAsString).trim().toLowerCase(Locale.ROOT);
		if (!TicketAdminCommand.ID.matcher(id).matches() || TicketBot.inst().getConfig().type(id) != null) {
			this.reply(event, TicketAdminCommand.ERROR, "L'identifiant `" + id + "` est invalide ou déjà utilisé (minuscules, chiffres et tirets uniquement).");
			return;
		}

		TicketBot.inst().getConfig().create(id);
		this.reply(event, TicketAdminCommand.SUCCESS, "Le type `" + id + "` a été créé dans `config/type/" + id + ".json`.");
	}

	private void createTeam(final SlashCommandInteractionEvent event) {
		final TypeConfig type = this.type(event);
		if (type == null) {
			return;
		}

		final String id = event.getOption("id", OptionMapping::getAsString).trim().toLowerCase(Locale.ROOT);
		final String name = event.getOption("name", OptionMapping::getAsString).trim();
		final Role role = event.getOption("role", OptionMapping::getAsRole);
		if (!TicketAdminCommand.ID.matcher(id).matches() || type.getTeams().containsKey(id)) {
			this.reply(event, TicketAdminCommand.ERROR, "L'identifiant `" + id + "` est invalide ou déjà utilisé dans `" + type.getId() + "`.");
			return;
		}

		type.getTeams().put(id, new TicketTeam(name, new LinkedHashSet<>(Collections.singleton(role.getIdLong()))));
		this.save();
		this.reply(event, TicketAdminCommand.SUCCESS, "L'équipe **" + name + "** (`" + id + "`) a été créée dans `" + type.getId() + "` avec le rôle " + role.getAsMention() + ".");
	}

	private void deleteTeam(final SlashCommandInteractionEvent event) {
		final TypeConfig type = this.type(event);
		final String team = type == null ? null : this.team(event, type);
		if (team == null) {
			return;
		}

		type.getTeams().remove(team);
		this.save();
		this.reply(event, TicketAdminCommand.SUCCESS, "L'équipe `" + team + "` a été supprimée de `" + type.getId() + "`.");
	}

	private ScopeConfig scope(final SlashCommandInteractionEvent event) {
		return event.getOption("type") == null ? TicketBot.inst().getConfig().global() : this.type(event);
	}

	private void editTeam(final SlashCommandInteractionEvent event, final boolean add) {
		final TypeConfig type = this.type(event);
		final String team = type == null ? null : this.team(event, type);
		if (team == null) {
			return;
		}

		final Role role = event.getOption("role", OptionMapping::getAsRole);
		TicketAdminCommand.edit(type.getTeams().get(team).getRoles(), role.getIdLong(), add);
		this.save();
		this.reply(event, TicketAdminCommand.SUCCESS, "Le rôle " + role.getAsMention() + (add ? " a été ajouté à" : " a été retiré de") + " l'équipe `" + team + "` de `" + type.getId() + "`.");
	}

	private void toggle(final SlashCommandInteractionEvent event, final boolean enabled) {
		final ScopeConfig scope = this.scope(event);
		if (scope == null) {
			return;
		}

		scope.setEnabled(enabled);
		this.save();
		this.reply(event, TicketAdminCommand.SUCCESS, "`" + scope.name() + "` est désormais **" + (enabled ? "activé" : "désactivé") + "**.");
	}

	private String team(final SlashCommandInteractionEvent event, final TypeConfig type) {
		final String id = event.getOption("team", OptionMapping::getAsString);
		if (type.getTeams().containsKey(id)) {
			return id;
		}

		this.reply(event, TicketAdminCommand.ERROR, "L'équipe `" + id + "` n'existe pas dans `" + type.getId() + "`.");
		return null;
	}

	private void reply(final IReplyCallback callback, final int color, final String text) {
		callback.replyEmbeds(this.embed(color, text)).setEphemeral(true).queue();
	}

	private void editCategory(final SlashCommandInteractionEvent event, final boolean add) {
		final String id = event.getOption("type", OptionMapping::getAsString);
		final GuildChannelUnion category = event.getOption("category", OptionMapping::getAsChannel);
		final TypeConfig owner = TicketBot.inst().getConfig().types().stream().filter(other -> !other.getId().equals(id) && other.getCategories().contains(category.getIdLong())).findFirst().orElse(null);
		if (add && owner != null) {
			this.reply(event, TicketAdminCommand.ERROR, "La catégorie **" + category.getName() + "** appartient déjà à `" + owner.getId() + "`.");
			return;
		}

		this.editSet(event, "category", TypeConfig::getCategories, add, "catégories");
	}

	private TextInput input(final String id, final TextInputStyle style, final String value, final boolean required, final int length) {
		return TextInput.create(id, style).setRequired(required).setMaxLength(length).setValue(value == null || value.isEmpty() ? null : value).build();
	}

	private void editSet(final SlashCommandInteractionEvent event, final String option, final Function<TypeConfig, Set<Long>> set, final boolean add, final String list) {
		final TypeConfig type = this.type(event);
		if (type == null) {
			return;
		}

		final IMentionable target = event.getOption(option, OptionMapping::getAsMentionable);
		TicketAdminCommand.edit(set.apply(type), target.getIdLong(), add);
		this.save();
		this.reply(event, TicketAdminCommand.SUCCESS, target.getAsMention() + (add ? " a été ajouté aux " : " a été retiré des ") + list + " de `" + type.getId() + "`.");
	}

	private static String fallback(final String value) {
		return value.isEmpty() ? "*Aucun*" : value;
	}

	private static String mentions(final Collection<Long> ids, final String prefix) {
		return ids.stream().map(id -> "<" + prefix + id + ">").collect(Collectors.joining(" "));
	}

	private static void edit(final Set<Long> ids, final long id, final boolean add) {
		if (add) {
			ids.add(id);
		} else {
			ids.remove(id);
		}
	}

}