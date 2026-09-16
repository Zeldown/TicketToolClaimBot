package fr.zeldown.ticketbot.stats;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import fr.zeldown.ticketbot.config.ConfigService;
import fr.zeldown.ticketbot.config.StatsConfig;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public final class StatsService {

	private static final Gson              GSON = new Gson();
	private static final String            EXTENSION = ".jsonl";
	private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

	private final ConfigService config;

	public void log(final @NonNull StatsEvent event) {
		final StatsConfig stats = this.config.global().getStats();
		if (!stats.isEnabled()) {
			return;
		}

		final Path file = Paths.get(stats.getDirectory()).resolve(this.month(event.getTime()) + StatsService.EXTENSION);
		try {
			Files.createDirectories(file.getParent());
			Files.write(file, (StatsService.GSON.toJson(event) + "\n").getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (final IOException e) {
			log.error("Unable to write the stats event", e);
		}
	}

	public @NonNull List<StatsEvent> events(final long since) {
		final List<StatsEvent> events = new ArrayList<>();
		for (final Path file : this.files(since)) {
			try {
				for (final String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
					final StatsEvent event = this.parse(line);
					if (event != null && event.getTime() >= since) {
						events.add(event);
					}
				}
			} catch (final IOException e) {
				log.error("Unable to read the stats file {}", file, e);
			}
		}
		return events;
	}

	public @NonNull List<TicketRecord> records(final long since) {
		final Map<Long, TicketRecord> records = new LinkedHashMap<>();
		for (final StatsEvent event : this.events(since)) {
			records.computeIfAbsent(event.getChannel(), TicketRecord::new).apply(event);
		}
		return new ArrayList<>(records.values());
	}

	private @NonNull String month(final long time) {
		return StatsService.MONTH.format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()));
	}

	private StatsEvent parse(final @NonNull String line) {
		if (line.trim().isEmpty()) {
			return null;
		}

		try {
			return StatsService.GSON.fromJson(line, StatsEvent.class);
		} catch (final JsonSyntaxException e) {
			log.warn("Ignoring a malformed stats line");
			return null;
		}
	}

	private @NonNull List<Path> files(final long since) {
		final Path directory = Paths.get(this.config.global().getStats().getDirectory());
		if (!Files.isDirectory(directory)) {
			return Collections.emptyList();
		}

		final String month = this.month(since);
		try (Stream<Path> files = Files.list(directory)) {
			return files.filter(file -> file.getFileName().toString().endsWith(StatsService.EXTENSION) && file.getFileName().toString().compareTo(month) >= 0).sorted().collect(Collectors.toList());
		} catch (final IOException e) {
			log.error("Unable to list the stats directory {}", directory, e);
			return Collections.emptyList();
		}
	}

}