package fr.zeldown.ticketbot.ticket;

import java.util.concurrent.TimeUnit;

import fr.zeldown.ticketbot.config.TypeConfig;
import fr.zeldown.ticketbot.stats.StatsService;
import fr.zeldown.ticketbot.stats.TicketRecord;
import lombok.NonNull;

public final class TicketQueue {

	private static final int    SAMPLE = 5;
	private static final long   WINDOW = TimeUnit.DAYS.toMillis(7L);
	private static final long   CEILING = TimeUnit.HOURS.toMillis(2L);
	private static final String NOTICE = "*Ce délai est une estimation calculée sur les derniers jours : il varie selon l'affluence et la disponibilité du staff, et ne constitue pas un engagement.*";

	private TicketQueue() {}

	public static @NonNull String eta(final long delay) {
		return delay <= 0L ? "" : "Le délai habituel de prise en charge est " + TicketQueue.round(delay) + ".";
	}

	public static @NonNull String notice(final long delay) {
		return delay <= 0L ? "" : TicketQueue.NOTICE;
	}

	public static @NonNull String queue(final long position) {
		if (position <= 0L) {
			return "Aucune autre demande n'est en attente avant la vôtre.";
		}
		return position == 1L ? "**1 demande** est en attente avant la vôtre." : "**" + position + " demandes** sont en attente avant la vôtre.";
	}

	public static long delay(final @NonNull StatsService stats, final @NonNull TypeConfig type) {
		final long[] waits = stats.records(System.currentTimeMillis() - TicketQueue.WINDOW).stream().filter(record -> type.getId().equals(record.getType())).mapToLong(TicketRecord::waitMs).filter(wait -> wait > 0L).sorted().toArray();
		if (waits.length < TicketQueue.SAMPLE) {
			return 0L;
		}

		final long median = waits[waits.length / 2];
		return median > TicketQueue.CEILING ? 0L : median;
	}

	private static @NonNull String round(final long millis) {
		final long minutes = millis / 60000L;
		if (minutes < 5L) {
			return "de **moins de 5 minutes**";
		}

		if (minutes < 58L) {
			return "d'**environ " + (minutes + 2L) / 5L * 5L + " minutes**";
		}

		final long hours = (minutes + 30L) / 60L;
		return hours == 1L ? "d'**environ 1 heure**" : "d'**environ " + hours + " heures**";
	}

}