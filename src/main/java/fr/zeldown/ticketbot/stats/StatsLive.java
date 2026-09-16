package fr.zeldown.ticketbot.stats;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import fr.zeldown.ticketbot.config.ConfigService;
import fr.zeldown.ticketbot.config.StatsConfig;
import fr.zeldown.ticketbot.config.TypeConfig;
import fr.zeldown.ticketbot.ticket.TicketService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.FileUpload;

@Slf4j
@RequiredArgsConstructor
public final class StatsLive {

	private static final int    TOP = 10;
	private static final int    COLOR = 0x5865F2;
	private static final String FILE = "live.png";
	private static final long   WINDOW = TimeUnit.DAYS.toMillis(7L);
	private static final long   HISTORY = TimeUnit.DAYS.toMillis(90L);

	private final StatsService  stats;
	private final ConfigService config;
	private final TicketService tickets;

	private ScheduledExecutorService scheduler;

	public void shutdown() {
		if (this.scheduler != null) {
			this.scheduler.shutdownNow();
		}
	}

	public void start(final @NonNull JDA jda) {
		this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
			final Thread thread = new Thread(runnable, "ticket-stats-live");
			thread.setDaemon(true);
			return thread;
		});
		this.scheduler.scheduleAtFixedRate(() -> this.refresh(jda), 15L, Math.max(1, this.config.global().getStats().getRefresh()) * 60L, TimeUnit.SECONDS);
	}

	private void refresh(final @NonNull JDA jda) {
		final StatsConfig stats = this.config.global().getStats();
		final TextChannel channel = stats.isEnabled() && stats.getChannel() != 0L ? jda.getTextChannelById(stats.getChannel()) : null;
		final Guild guild = jda.getGuildById(this.config.global().getGuild());
		if (channel == null || guild == null) {
			return;
		}

		try {
			final List<TicketRecord> records = this.stats.records(System.currentTimeMillis() - StatsLive.HISTORY);
			final List<Long> top = records.stream().filter(record -> record.isOpen() && record.getStaff() != 0L).collect(Collectors.groupingBy(TicketRecord::getStaff, LinkedHashMap::new, Collectors.counting())).entrySet().stream().sorted(Map.Entry.<Long, Long>comparingByValue().reversed()).limit(StatsLive.TOP).map(Map.Entry::getKey).collect(Collectors.toList());
			if (top.isEmpty()) {
				this.publish(channel, this.embed(stats), this.chart(guild, records, top, Collections.emptyList()));
				return;
			}

			RestAction.allOf(top.stream().map(id -> jda.retrieveUserById(id).onErrorMap(error -> null)).collect(Collectors.toList())).queue(users -> this.publish(channel, this.embed(stats), this.chart(guild, records, top, users)));
		} catch (final RuntimeException e) {
			log.error("Unable to refresh the live stats message", e);
		}
	}

	private @NonNull MessageEmbed embed(final @NonNull StatsConfig stats) {
		final long next = System.currentTimeMillis() / 1000L + Math.max(1, stats.getRefresh()) * 60L;
		return new EmbedBuilder().setColor(StatsLive.COLOR).setTitle("🎫 Tickets en direct").setDescription("Prochaine actualisation <t:" + next + ":R>").setImage("attachment://" + StatsLive.FILE).setTimestamp(Instant.now()).setFooter("Actualisé toutes les " + Math.max(1, stats.getRefresh()) + " min").build();
	}

	private void send(final @NonNull TextChannel channel, final @NonNull MessageEmbed embed, final byte[] chart) {
		channel.sendMessageEmbeds(embed).setFiles(FileUpload.fromData(chart, StatsLive.FILE)).queue(message -> {
			this.config.global().getStats().setMessage(message.getIdLong());
			this.config.save();
		});
	}

	private void publish(final @NonNull TextChannel channel, final @NonNull MessageEmbed embed, final byte[] chart) {
		final long message = this.config.global().getStats().getMessage();
		if (message == 0L) {
			this.send(channel, embed, chart);
			return;
		}

		channel.editMessageEmbedsById(message, embed).setAttachments(FileUpload.fromData(chart, StatsLive.FILE)).queue(null, error -> this.send(channel, embed, chart));
	}

	private byte[] chart(final @NonNull Guild guild, final @NonNull List<TicketRecord> records, final @NonNull List<Long> top, final @NonNull List<User> users) {
		final StringBuilder subtitle = new StringBuilder();
		long open = 0L;
		long waiting = 0L;
		long oldest = 0L;
		for (final TypeConfig type : this.config.types()) {
			final List<TextChannel> channels = guild.getTextChannels().stream().filter(channel -> type.getCategories().contains(channel.getParentCategoryIdLong())).collect(Collectors.toList());
			final List<TextChannel> pending = channels.stream().filter(this.tickets::isWaiting).collect(Collectors.toList());
			final long since = pending.stream().mapToLong(channel -> channel.getTimeCreated().toInstant().toEpochMilli()).min().orElse(0L);
			oldest = oldest == 0L || (since != 0L && since < oldest) ? since : oldest;
			open += channels.size();
			waiting += pending.size();
			subtitle.append(subtitle.length() == 0 ? "" : " · ").append(type.getId()).append(" ").append(channels.size()).append(" ouverts, ").append(pending.size()).append(" en attente");
		}

		final long window = System.currentTimeMillis() - StatsLive.WINDOW;
		final List<String[]> rows = new ArrayList<>();
		for (int index = 0; index < top.size(); index++) {
			final long id = top.get(index);
			final List<TicketRecord> handled = records.stream().filter(record -> record.getStaff() == id).collect(Collectors.toList());
			final long[] waits = handled.stream().filter(record -> record.getOpen() >= window).mapToLong(TicketRecord::waitMs).filter(wait -> wait > 0L).sorted().toArray();
			final User user = index < users.size() ? users.get(index) : null;
			rows.add(new String[] { user == null ? "Inconnu" : user.getEffectiveName(), Long.toString(handled.stream().filter(TicketRecord::isOpen).count()), Long.toString(handled.stream().filter(record -> record.getOpen() >= window).count()), waits.length == 0 ? "—" : StatsFormat.duration(waits[waits.length / 2]) });
		}

		final List<ChartKpi> kpis = Arrays.asList(ChartKpi.of("Ouverts", Long.toString(open)), ChartKpi.of("En attente", Long.toString(waiting)), ChartKpi.of("Pris en charge", Long.toString(open - waiting)), ChartKpi.of("Plus ancienne attente", oldest == 0L ? "—" : StatsFormat.duration(System.currentTimeMillis() - oldest)));
		return ChartRenderer.table("Tickets en direct", subtitle.toString(), kpis, Arrays.asList("Staff", "En cours", "Pris en charge (7j)", "Médiane (7j)"), rows);
	}

}