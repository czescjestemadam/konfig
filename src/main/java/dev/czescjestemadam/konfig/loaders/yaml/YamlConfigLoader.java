package dev.czescjestemadam.konfig.loaders.yaml;

import dev.czescjestemadam.konfig.Config;
import dev.czescjestemadam.konfig.exceptions.ConfigLoadException;
import dev.czescjestemadam.konfig.exceptions.ConfigLoaderException;
import dev.czescjestemadam.konfig.loaders.AbstractConfigLoader;
import dev.czescjestemadam.konfig.serializer.SerializerMappings;
import org.snakeyaml.engine.v2.api.*;
import org.snakeyaml.engine.v2.common.FlowStyle;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;

public class YamlConfigLoader extends AbstractConfigLoader {
	private static final LoadSettings LOAD_SETTINGS = LoadSettings.builder()
			.setParseComments(true)
			.build();

	private static final DumpSettings DUMP_SETTINGS = DumpSettings.builder()
			.setDefaultFlowStyle(FlowStyle.BLOCK)
			.setIndentWithIndicator(true)
			.setIndent(2)
			.setDumpComments(true)
			.build();

	private final Load loader = new Load(LOAD_SETTINGS);
	private final Dump dumper = new Dump(DUMP_SETTINGS, new YamlRepresenter(DUMP_SETTINGS));

	public YamlConfigLoader(SerializerMappings mappings) {
		super(mappings);
	}

	@Override
	protected void load(Path path, Class<? extends Config> configClass, Config config) {
		final Map<?, ?> data = loadYamlData(path);

		if (!data.isEmpty()) {
			loadData(configClass, config, data);
		}
	}

	@Override
	protected void save(Path path, Class<? extends Config> configClass, Config config) {
		final Map<Object, Object> data = saveData(configClass, config);

		saveYamlData(path, data);
	}

	protected Map<?, ?> loadYamlData(Path path) {
		final Object object;
		try (final Reader reader = Files.newBufferedReader(path)) {
			object = loader.loadFromReader(reader);
		} catch (final NoSuchFileException e) {
			return Map.of();
		} catch (final IOException e) {
			throw new ConfigLoaderException("Config loading failed", e);
		}

		if (object == null) {
			return Map.of();
		}

		if (!(object instanceof Map<?, ?> data)) {
			throw new ConfigLoadException("Loaded YAML data is invalid, expected a mapping at the root of " + path);
		}

		return data;
	}

	protected void saveYamlData(Path path, Object data) {
		final Path parent = path.toAbsolutePath().getParent();
		if (parent != null) {
			try {
				Files.createDirectories(parent);
			} catch (final IOException e) {
				throw new ConfigLoaderException("Failed to create parent directory for config", e);
			}
		}

		final Path tempPath = path.resolveSibling(path.getFileName() + ".tmp");
		try {
			try (final YamlOutputStreamWriter writer = new YamlOutputStreamWriter(
					Files.newOutputStream(tempPath),
					StandardCharsets.UTF_8
			)) {
				dumper.dump(data, writer);
			}

			try {
				Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (final AtomicMoveNotSupportedException e) {
				Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (final IOException | RuntimeException e) {
			try {
				Files.deleteIfExists(tempPath);
			} catch (final IOException suppressed) {
				e.addSuppressed(suppressed);
			}

			throw new ConfigLoaderException("Failed to save config", e);
		}
	}
}
