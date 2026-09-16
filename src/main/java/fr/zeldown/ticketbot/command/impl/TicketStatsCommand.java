package fr.zeldown.ticketbot.command.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import fr.zeldown.ticketbot.TicketBot;
import fr.zeldown.ticketbot.command.SlashCommand;
import fr.zeldown.ticketbot.config.TypeConfig;
import fr.zeldown.ticketbot.stats.ChartKpi;
import fr.zeldown.ticketbot.stats.ChartRenderer;
import fr.zeldown.ticketbot.stats.StatsEvent;
import fr.zeldown.ticketbot.stats.StatsFormat;
import fr.zeldown.ticketbot.stats.StatsPeriod;
import fr.zeldown.ticketbot.stats.StatsView;
import fr.zeldown.ticketbot.stats.TeamRecord;
import fr.zeldown.ticketbot.stats.TicketRecord;
import lombok.NonNull;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.FileUpload;

public final class TicketStatsCommand implements SlashCommand {

	private static final int               TOP = 10;
	private static final String            ALL = "all";
	private static final int               COLOR = 0x5865F2;
	private static final String            CHART = "chart.png";
	private static final String            NAME = "ticket-stats";
	private static final DateTimeFormatter LABEL = DateTimeFormatter.ofPattern("dd/MM");
	private static final String[]          DAYS = { "Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche" };

	@Override
	public @NonNull SlashCommandData data() {
		return Commands.slash(TicketStatsCommand.NAME, "Tableau de bord des statistiques de tickets").setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR)).addOption(OptionType.USER, "staff", "Limiter les statistiques à un membre du staff", false);
	}

	@Override
	public void button(final @NonNull ButtonInteractionEvent event) {
		final String[] parts = event.getComponentId().split(":", 6);
		event.deferEdit().queue(hook -> this.render(hook, StatsPeriod.of(parts[2]), StatsView.of(parts[3]), Long.parseLong(parts[4]), parts[5]));
	}

	@Override
	public void select(final @NonNull StringSelectInteractionEvent event) {
		final String[] parts = event.getComponentId().split(":", 5);
		final String value = event.getValues().get(0);
		final boolean view = "view".equals(parts[1]);
		event.deferEdit().queue(hook -> this.render(hook, StatsPeriod.of(parts[2]), StatsView.of(view ? value : parts[3]), Long.parseLong(view ? parts[3] : parts[4]), view ? parts[4] : value));
	}

	@Override
	public void execute(final @NonNull SlashCommandInteractionEvent event) {
		if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
			event.replyEmbeds(new EmbedBuilder().setColor(0xE74C3C).setDescription("Vous devez être administrateur pour utiliser cette commande.").build()).setEphemeral(true).queue();
			return;
		}

		final long staff = event.getOption("staff", 0L, option -> option.getAsUser().getIdLong());
		event.deferReply(true).queue(hook -> this.render(hook, StatsPeriod.MONTH, staff == 0L ? StatsView.OVERVIEW : StatsView.PROFILE, staff, TicketStatsCommand.ALL));
	}

	private byte[] hours(final @NonNull EmbedBuilder embed, final @NonNull String subtitle, final @NonNull List<TicketRecord> records) {
		final long[][] values = new long[7][24];
		for (final TicketRecord record : records) {
			final ZonedDateTime moment = Instant.ofEpochMilli(record.getOpen()).atZone(ZoneId.systemDefault());
			values[moment.getDayOfWeek().getValue() - 1][moment.getHour()]++;
		}

		long best = 0L;
		String slot = "—";
		for (int day = 0; day < 7; day++) {
			for (int hour = 0; hour < 24; hour++) {
				if (values[day][hour] > best) {
					best = values[day][hour];
					slot = TicketStatsCommand.DAYS[day] + " " + hour + " h";
				}
			}
		}

		embed.setDescription("Créneau le plus chargé : **" + slot + "** avec `" + best + "` tickets.");
		return ChartRenderer.heatmap("Tickets ouverts par jour et par heure", subtitle, values, Arrays.asList(TicketStatsCommand.DAYS));
	}

	private byte[] quality(final @NonNull EmbedBuilder embed, final @NonNull String subtitle, final @NonNull List<TicketRecord> records) {
		final long[] waits = records.stream().mapToLong(TicketRecord::waitMs).filter(wait -> wait > 0L).sorted().toArray();
		final long[] buckets = new long[5];
		for (final long wait : waits) {
			final long minutes = wait / 60000L;
			buckets[minutes < 1L ? 0 : minutes < 5L ? 1 : minutes < 15L ? 2 : minutes < 60L ? 3 : 4]++;
		}

		final List<ChartKpi> kpis = Arrays.asList(ChartKpi.of("Médiane", StatsFormat.duration(TicketStatsCommand.quantile(waits, 0.5D))), ChartKpi.of("p90", StatsFormat.duration(TicketStatsCommand.quantile(waits, 0.9D))), ChartKpi.of("p99", StatsFormat.duration(TicketStatsCommand.quantile(waits, 0.99D))), ChartKpi.of("Jamais pris", Long.toString(records.stream().filter(record -> !record.isOpen() && record.getClaim() == 0L).count())), ChartKpi.of("Transferts 3+", Long.toString(records.stream().filter(record -> record.getTransfers() >= 3).count())));
		embed.setDescription("Délais de prise en charge mesurés sur **" + waits.length + "** tickets.");
		return ChartRenderer.bars("Répartition des délais de prise en charge", subtitle, kpis, Arrays.asList("moins d'1 min", "1 à 5 min", "5 à 15 min", "15 à 60 min", "plus d'1 h"), buckets);
	}

	private @NonNull List<ActionRow> rows(final @NonNull StatsPeriod period, final @NonNull StatsView view, final long staff, final @NonNull String type) {
		final List<Button> buttons = new ArrayList<>();
		for (final StatsPeriod value : StatsPeriod.values()) {
			final String id = TicketStatsCommand.NAME + ":period:" + value.getKey() + ":" + view.getKey() + ":" + staff + ":" + type;
			final String label = value == StatsPeriod.ALL ? "Tout" : value.getKey();
			buttons.add(value == period ? Button.primary(id, label) : Button.secondary(id, label));
		}

		final StringSelectMenu.Builder views = StringSelectMenu.create(TicketStatsCommand.NAME + ":view:" + period.getKey() + ":" + staff + ":" + type).setDefaultValues(Collections.singleton(view.getKey()));
		for (final StatsView value : StatsView.values()) {
			if (value != StatsView.PROFILE || staff != 0L) {
				views.addOption(value.getLabel(), value.getKey(), Emoji.fromUnicode(value.getIcon()));
			}
		}

		final StringSelectMenu.Builder types = StringSelectMenu.create(TicketStatsCommand.NAME + ":type:" + period.getKey() + ":" + view.getKey() + ":" + staff).setDefaultValues(Collections.singleton(type));
		types.addOption("Tous les types", TicketStatsCommand.ALL);
		for (final TypeConfig value : TicketBot.inst().getConfig().types()) {
			types.addOption(value.getId(), value.getId());
		}
		return Arrays.asList(ActionRow.of(buttons), ActionRow.of(views.build()), ActionRow.of(types.build()));
	}

	private byte[] teams(final @NonNull EmbedBuilder embed, final @NonNull String subtitle, final @NonNull StatsPeriod period, final @NonNull List<TicketRecord> records) {
		final Set<Long> channels = records.stream().map(TicketRecord::getChannel).collect(Collectors.toSet());
		final Map<String, TeamRecord> teams = TeamRecord.segments(TicketBot.inst().getStats().events(period.since()).stream().filter(event -> channels.contains(event.getChannel())).collect(Collectors.toList()));
		if (teams.isEmpty()) {
			embed.setDescription("Aucun transfert sur cette période.");
			return null;
		}

		final List<TeamRecord> sorted = teams.values().stream().sorted((first, second) -> Integer.compare(second.getTransfers(), first.getTransfers())).collect(Collectors.toList());
		final long transferred = sorted.stream().mapToLong(TeamRecord::getTransfers).sum();
		final List<ChartKpi> kpis = Arrays.asList(ChartKpi.of("Transferts", Long.toString(transferred)), ChartKpi.of("Équipes", Integer.toString(sorted.size())), ChartKpi.of("Tickets transférés", TicketStatsCommand.percent(records.stream().filter(record -> record.getTransfers() > 0).count(), records.size())), ChartKpi.of("Jamais repris", Long.toString(sorted.stream().mapToLong(TeamRecord::getOrphans).sum())));
		final List<String[]> lines = sorted.stream().limit(TicketStatsCommand.TOP).map(team -> new String[] { team.getTeam(), Integer.toString(team.getTransfers()), StatsFormat.duration(team.pickup()), StatsFormat.duration(team.handle()), Integer.toString(team.getOrphans()) }).collect(Collectors.toList());
		embed.setDescription("**" + transferred + "** transferts sur la période, reçus par `" + sorted.size() + "` équipes.");
		return ChartRenderer.table("Délais par équipe", subtitle, kpis, Arrays.asList("Équipe", "Transferts", "Reprise", "Traitement", "Jamais repris"), lines);
	}

	private void render(final @NonNull InteractionHook hook, final @NonNull StatsPeriod period, final @NonNull StatsView view, final long staff, final @NonNull String type) {
		final long since = period.since();
		final StatsView actual = view == StatsView.PROFILE && staff == 0L ? StatsView.OVERVIEW : view;
		final List<TicketRecord> records = TicketBot.inst().getStats().records(since).stream().filter(record -> record.getOpen() >= since && (TicketStatsCommand.ALL.equals(type) || type.equals(record.getType())) && (staff == 0L || record.getStaff() == staff)).collect(Collectors.toList());
		final EmbedBuilder embed = new EmbedBuilder().setColor(TicketStatsCommand.COLOR).setTitle(actual.getIcon() + " Statistiques · " + actual.getLabel() + " · " + period.getLabel()).setTimestamp(Instant.now()).setFooter((TicketStatsCommand.ALL.equals(type) ? "Tous les types" : "Type " + type) + (staff == 0L ? "" : " · un seul staff"));
		if (records.isEmpty()) {
			embed.setDescription(staff == 0L ? "Aucune donnée sur cette période." : "Aucun ticket pris en charge par <@" + staff + "> sur cette période.");
			this.publish(hook, embed, null, period, actual, staff, type);
			return;
		}

		if (actual == StatsView.PROFILE) {
			this.profile(hook, embed, period, staff, type, records);
			return;
		}

		if (actual == StatsView.STAFF) {
			this.staff(hook, embed, period, staff, type, records);
			return;
		}

		this.publish(hook, embed, this.chart(embed, actual, period, staff, type, records), period, actual, staff, type);
	}

	private byte[] chart(final @NonNull EmbedBuilder embed, final @NonNull StatsView view, final @NonNull StatsPeriod period, final long staff, final @NonNull String type, final @NonNull List<TicketRecord> records) {
		final String subtitle = TicketStatsCommand.subtitle(period, type);
		if (view == StatsView.TEAMS) {
			return this.teams(embed, subtitle, period, records);
		}

		if (view == StatsView.HOURS) {
			return this.hours(embed, subtitle, records);
		}

		if (view == StatsView.QUALITY) {
			return this.quality(embed, subtitle, records);
		}
		return this.overview(embed, subtitle, period, staff, type, records);
	}

	private byte[] overview(final @NonNull EmbedBuilder embed, final @NonNull String subtitle, final @NonNull StatsPeriod period, final long staff, final @NonNull String type, final @NonNull List<TicketRecord> records) {
		final long closed = records.stream().filter(record -> !record.isOpen()).count();
		final long[] waits = records.stream().mapToLong(TicketRecord::waitMs).filter(wait -> wait > 0L).sorted().toArray();
		final long sla = TicketBot.inst().getConfig().global().getStats().getSla() * 60000L;
		final long within = Arrays.stream(waits).filter(wait -> wait <= sla).count();
		final long[] handled = records.stream().mapToLong(TicketRecord::handleMs).filter(handle -> handle > 0L).sorted().toArray();
		embed.setDescription("**" + records.size() + "** tickets ouverts sur la période, dont `" + (records.size() - closed) + "` encore en cours.");
		embed.addField("Tickets", "Ouverts `" + records.size() + "`\nFermés `" + closed + "`\nEn cours `" + (records.size() - closed) + "`", true);
		embed.addField("Prise en charge", "Médiane `" + StatsFormat.duration(TicketStatsCommand.quantile(waits, 0.5D)) + "`\np90 `" + StatsFormat.duration(TicketStatsCommand.quantile(waits, 0.9D)) + "`\nSLA `" + TicketStatsCommand.percent(within, waits.length) + "`", true);
		embed.addField("Qualité", "Transférés `" + TicketStatsCommand.percent(records.stream().filter(record -> record.getTransfers() > 0).count(), records.size()) + "`\nJamais pris `" + TicketStatsCommand.percent(records.stream().filter(record -> !record.isOpen() && record.getClaim() == 0L).count(), records.size()) + "`\nTraitement `" + StatsFormat.duration(TicketStatsCommand.quantile(handled, 0.5D)) + "`", true);

		final int days = Math.min(90, period.getDays() == 0 ? 90 : period.getDays());
		final List<String> labels = new ArrayList<>();
		final long[] opened = new long[days];
		final long[] finished = new long[days];
		for (int index = 0; index < days; index++) {
			labels.add(LocalDate.now().minusDays(days - 1L - index).format(TicketStatsCommand.LABEL));
		}

		for (final TicketRecord record : records) {
			TicketStatsCommand.count(opened, days, record.getOpen());
			TicketStatsCommand.count(finished, days, record.getClose() == 0L ? record.getDelete() : record.getClose());
		}

		final long before = TicketStatsCommand.before(period, staff, type);
		final long change = before == 0L ? 0L : Math.round((records.size() - before) * 100D / before);
		final List<ChartKpi> kpis = Arrays.asList(new ChartKpi("Tickets ouverts", Long.toString(records.size()), before == 0L ? null : (change > 0L ? "+" : "") + change + " %", change <= 0L), ChartKpi.of("En cours", Long.toString(records.size() - closed)), ChartKpi.of("Prise en charge", StatsFormat.duration(TicketStatsCommand.quantile(waits, 0.5D))), ChartKpi.of("Objectif " + TicketStatsCommand.objective(TicketBot.inst().getConfig().global().getStats().getSla()), TicketStatsCommand.percent(within, waits.length)));
		return ChartRenderer.trend("Tickets ouverts et fermés par jour", subtitle, kpis, labels, opened, finished, "Ouverts", "Fermés");
	}

	private void staff(final @NonNull InteractionHook hook, final @NonNull EmbedBuilder embed, final @NonNull StatsPeriod period, final long filter, final @NonNull String type, final @NonNull List<TicketRecord> records) {
		final Map<Long, List<TicketRecord>> claimed = records.stream().filter(record -> record.getStaff() != 0L).collect(Collectors.groupingBy(TicketRecord::getStaff, LinkedHashMap::new, Collectors.toList()));
		if (claimed.isEmpty()) {
			embed.setDescription("Aucune prise en charge sur cette période.");
			this.publish(hook, embed, null, period, StatsView.STAFF, filter, type);
			return;
		}

		final List<Map.Entry<Long, List<TicketRecord>>> sorted = claimed.entrySet().stream().sorted((first, second) -> Integer.compare(second.getValue().size(), first.getValue().size())).limit(TicketStatsCommand.TOP).collect(Collectors.toList());
		final List<RestAction<User>> requests = sorted.stream().map(entry -> TicketBot.inst().getJda().retrieveUserById(entry.getKey()).onErrorMap(error -> null)).collect(Collectors.toList());
		final long[] overall = records.stream().mapToLong(TicketRecord::waitMs).filter(wait -> wait > 0L).sorted().toArray();
		final long handled = records.stream().filter(record -> record.getStaff() != 0L).count();
		final long running = records.stream().filter(record -> record.getStaff() != 0L && record.isOpen()).count();
		final List<ChartKpi> kpis = Arrays.asList(ChartKpi.of("Staff actifs", Integer.toString(claimed.size())), ChartKpi.of("Prises en charge", Long.toString(handled)), ChartKpi.of("Médiane", StatsFormat.duration(TicketStatsCommand.quantile(overall, 0.5D))), ChartKpi.of("En cours", Long.toString(running)));
		RestAction.allOf(requests).queue(users -> {
			final List<String[]> lines = new ArrayList<>();
			for (int index = 0; index < sorted.size(); index++) {
				final List<TicketRecord> tickets = sorted.get(index).getValue();
				final long[] waits = tickets.stream().mapToLong(TicketRecord::waitMs).filter(wait -> wait > 0L).sorted().toArray();
				lines.add(new String[] { users.get(index) == null ? "Inconnu" : users.get(index).getEffectiveName(), Integer.toString(tickets.size()), StatsFormat.duration(TicketStatsCommand.quantile(waits, 0.5D)), StatsFormat.duration(TicketStatsCommand.quantile(waits, 0.9D)), Long.toString(tickets.stream().filter(TicketRecord::isOpen).count()) });
			}

			embed.setDescription("**" + handled + "** prises en charge par `" + claimed.size() + "` membres du staff sur la période.");
			this.publish(hook, embed, ChartRenderer.table("Prises en charge par staff", TicketStatsCommand.subtitle(period, type), kpis, Arrays.asList("Staff", "Prises en charge", "Médiane", "p90", "En cours"), lines), period, StatsView.STAFF, filter, type);
		});
	}

	private void profile(final @NonNull InteractionHook hook, final @NonNull EmbedBuilder embed, final @NonNull StatsPeriod period, final long staff, final @NonNull String type, final @NonNull List<TicketRecord> records) {
		final long since = period.since();
		final List<StatsEvent> events = TicketBot.inst().getStats().events(since).stream().filter(event -> event.getStaff() == staff && (TicketStatsCommand.ALL.equals(type) || type.equals(event.getType()))).collect(Collectors.toList());
		final long[] waits = records.stream().mapToLong(TicketRecord::waitMs).filter(wait -> wait > 0L).sorted().toArray();
		final long[] handled = records.stream().mapToLong(TicketRecord::handleMs).filter(handle -> handle > 0L).sorted().toArray();
		final long closed = records.stream().filter(record -> !record.isOpen()).count();
		final long claims = TicketStatsCommand.total(events, StatsEvent.CLAIM);
		embed.setDescription("<@" + staff + "> — `" + claims + "` prises en charge sur la période, dont `" + (records.size() - closed) + "` encore en cours.");
		embed.addField("Délais", "Médiane `" + StatsFormat.duration(TicketStatsCommand.quantile(waits, 0.5D)) + "`\np90 `" + StatsFormat.duration(TicketStatsCommand.quantile(waits, 0.9D)) + "`\nTraitement `" + StatsFormat.duration(TicketStatsCommand.quantile(handled, 0.5D)) + "`", true);
		embed.addField("Actions", "Transferts `" + TicketStatsCommand.total(events, StatsEvent.TRANSFER) + "`\nAjouts `" + TicketStatsCommand.total(events, StatsEvent.ADD) + "`\nRetraits `" + TicketStatsCommand.total(events, StatsEvent.REMOVE) + "`", true);
		embed.addField("Tickets", "Pris en charge `" + records.size() + "`\nFermés `" + closed + "`\nRe-transférés `" + records.stream().filter(record -> record.getTransfers() > 0).count() + "`", true);

		final int days = Math.min(90, period.getDays() == 0 ? 90 : period.getDays());
		final List<String> labels = new ArrayList<>();
		final long[] taken = new long[days];
		final long[] finished = new long[days];
		for (int index = 0; index < days; index++) {
			labels.add(LocalDate.now().minusDays(days - 1L - index).format(TicketStatsCommand.LABEL));
		}

		for (final StatsEvent event : events) {
			if (StatsEvent.CLAIM.equals(event.getEvent())) {
				TicketStatsCommand.count(taken, days, event.getTime());
			}
		}

		for (final TicketRecord record : records) {
			TicketStatsCommand.count(finished, days, record.getClose() == 0L ? record.getDelete() : record.getClose());
		}

		TicketBot.inst().getJda().retrieveUserById(staff).onErrorMap(error -> null).queue(user -> {
			final List<ChartKpi> kpis = Arrays.asList(ChartKpi.of("Pris en charge", Long.toString(claims)), ChartKpi.of("Médiane", StatsFormat.duration(TicketStatsCommand.quantile(waits, 0.5D))), ChartKpi.of("p90", StatsFormat.duration(TicketStatsCommand.quantile(waits, 0.9D))), ChartKpi.of("Traitement", StatsFormat.duration(TicketStatsCommand.quantile(handled, 0.5D))));
			final String title = "Activité de " + (user == null ? "ce staff" : user.getEffectiveName());
			this.publish(hook, embed, ChartRenderer.trend(title, TicketStatsCommand.subtitle(period, type), kpis, labels, taken, finished, "Pris en charge", "Fermés"), period, StatsView.PROFILE, staff, type);
		});
	}

	private void publish(final @NonNull InteractionHook hook, final @NonNull EmbedBuilder embed, final byte[] chart, final @NonNull StatsPeriod period, final @NonNull StatsView view, final long staff, final @NonNull String type) {
		if (chart == null) {
			hook.editOriginalEmbeds(embed.build()).setAttachments().setComponents(this.rows(period, view, staff, type)).queue();
			return;
		}

		hook.editOriginalEmbeds(embed.setImage("attachment://" + TicketStatsCommand.CHART).build()).setAttachments(FileUpload.fromData(chart, TicketStatsCommand.CHART)).setComponents(this.rows(period, view, staff, type)).queue();
	}

	private static @NonNull String objective(final int minutes) {
		if (minutes < 60) {
			return minutes + " min";
		}
		return minutes % 60 == 0 ? minutes / 60 + " h" : minutes / 60 + " h " + minutes % 60;
	}

	private static long quantile(final long[] values, final double ratio) {
		return values.length == 0 ? 0L : values[(int) Math.min(values.length - 1L, Math.round(ratio * (values.length - 1)))];
	}

	private static @NonNull String percent(final long part, final long total) {
		return total == 0L ? "—" : Math.round(part * 100D / total) + " %";
	}

	private static void count(final long[] values, final int days, final long time) {
		final int index = time == 0L ? -1 : days - 1 - (int) TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - time);
		if (index >= 0 && index < days) {
			values[index]++;
		}
	}

	private static long total(final @NonNull List<StatsEvent> events, final @NonNull String name) {
		return events.stream().filter(event -> name.equals(event.getEvent())).count();
	}

	private static @NonNull String subtitle(final @NonNull StatsPeriod period, final @NonNull String type) {
		return period.getLabel() + " · " + (TicketStatsCommand.ALL.equals(type) ? "tous les types" : "type " + type);
	}

	private static long before(final @NonNull StatsPeriod period, final long staff, final @NonNull String type) {
		if (period.getDays() == 0) {
			return 0L;
		}

		final long since = period.since();
		final long previous = period.previous();
		return TicketBot.inst().getStats().records(previous).stream().filter(record -> record.getOpen() >= previous && record.getOpen() < since && (TicketStatsCommand.ALL.equals(type) || type.equals(record.getType())) && (staff == 0L || record.getStaff() == staff)).count();
	}

}