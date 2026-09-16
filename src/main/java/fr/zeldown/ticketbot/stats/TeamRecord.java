package fr.zeldown.ticketbot.stats;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public final class TeamRecord {

	private final String     team;
	private int              orphans;
	private int              transfers;
	private final List<Long> pickups = new ArrayList<>();
	private final List<Long> handles = new ArrayList<>();

	public long pickup() {
		return TeamRecord.median(this.pickups);
	}

	public long handle() {
		return TeamRecord.median(this.handles);
	}

	public static @NonNull Map<String, TeamRecord> segments(final @NonNull List<StatsEvent> events) {
		final Map<String, TeamRecord> teams = new LinkedHashMap<>();
		for (final List<StatsEvent> history : events.stream().sorted(Comparator.comparingLong(StatsEvent::getTime)).collect(Collectors.groupingBy(StatsEvent::getChannel, LinkedHashMap::new, Collectors.toList())).values()) {
			TeamRecord current = null;
			long claimed = 0L;
			long opened = 0L;
			for (final StatsEvent event : history) {
				if (StatsEvent.TRANSFER.equals(event.getEvent()) && event.getTeam() != null) {
					if (current != null && claimed == 0L) {
						current.orphans++;
					}

					current = teams.computeIfAbsent(event.getTeam(), TeamRecord::new);
					current.transfers++;
					opened = event.getTime();
					claimed = 0L;
				} else if (current == null) {
					continue;
				} else if (StatsEvent.CLAIM.equals(event.getEvent()) && claimed == 0L) {
					claimed = event.getTime();
					current.pickups.add(claimed - opened);
				} else if (StatsEvent.CLOSE.equals(event.getEvent()) || StatsEvent.DELETE.equals(event.getEvent())) {
					if (claimed == 0L) {
						current.orphans++;
					} else {
						current.handles.add(event.getTime() - claimed);
					}

					current = null;
					claimed = 0L;
				}
			}
		}
		return teams;
	}

	private static long median(final @NonNull List<Long> values) {
		return TicketRecord.quantile(values.stream().mapToLong(Long::longValue).sorted().toArray(), 0.5D);
	}

}