package fr.zeldown.ticketbot.config;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import lombok.NonNull;

public final class ConfigService {

	private static final String TYPES = "type";
	private static final String EXTENSION = ".json";
	private static final String GLOBAL = "global.json";
	private static final Gson   GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private final Path directory;

	private volatile GlobalConfig            global;
	private volatile Map<String, TypeConfig> types;

	public ConfigService(final @NonNull Path directory) {
		this.directory = directory;
		this.reload();
	}

	public synchronized void save() {
		this.write(this.directory.resolve(ConfigService.GLOBAL), this.global);
		for (final TypeConfig type : this.types.values()) {
			this.write(this.directory.resolve(ConfigService.TYPES).resolve(type.getId() + ConfigService.EXTENSION), type);
		}
	}

	public synchronized void reload() {
		final Map<String, TypeConfig> types = new LinkedHashMap<>();
		for (final Path file : this.files()) {
			final String name = file.getFileName().toString();
			final TypeConfig type = this.read(file, TypeConfig.class, TypeConfig::new);
			type.setId(name.substring(0, name.length() - ConfigService.EXTENSION.length()));
			types.put(type.getId(), type);
		}

		this.global = this.read(this.directory.resolve(ConfigService.GLOBAL), GlobalConfig.class, GlobalConfig::new);
		this.types = types;
		this.save();
	}

	public @NonNull GlobalConfig global() {
		return this.global;
	}

	public @NonNull Collection<TypeConfig> types() {
		return this.types.values();
	}

	public TypeConfig type(final @NonNull String id) {
		return this.types.get(id);
	}

	public synchronized void create(final @NonNull String id) {
		final TypeConfig type = new TypeConfig();
		final Map<String, TypeConfig> types = new LinkedHashMap<>(this.types);
		type.setId(id);
		type.complete();
		types.put(id, type);
		this.types = types;
		this.save();
	}

	private @NonNull List<Path> files() {
		final Path folder = this.directory.resolve(ConfigService.TYPES);
		try {
			Files.createDirectories(folder);
			try (Stream<Path> files = Files.list(folder)) {
				return files.filter(file -> file.getFileName().toString().endsWith(ConfigService.EXTENSION)).sorted().collect(Collectors.toList());
			}
		} catch (final IOException e) {
			throw new UncheckedIOException("Unable to list " + folder, e);
		}
	}

	private void write(final @NonNull Path file, final @NonNull ScopeConfig config) {
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				ConfigService.GSON.toJson(config, writer);
			}
		} catch (final IOException e) {
			throw new UncheckedIOException("Unable to save " + file, e);
		}
	}

	private @NonNull <T extends ScopeConfig> T read(final @NonNull Path file, final @NonNull Class<T> type, final @NonNull Supplier<T> factory) {
		T config = null;
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				config = ConfigService.GSON.fromJson(reader, type);
			} catch (final IOException e) {
				throw new UncheckedIOException("Unable to load " + file, e);
			}
		}

		final T loaded = config == null ? factory.get() : config;
		loaded.complete();
		return loaded;
	}

}