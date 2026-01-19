package dev.czescjestemadam.konfig.loaders;

import dev.czescjestemadam.konfig.Config;

import java.nio.file.Path;

public interface ConfigLoader {
	void load(Config config);

	void load(Path path, Config config);

	void load(Class<? extends Config> config);

	void load(Path path, Class<? extends Config> config);


	void save(Config config);

	void save(Path path, Config config);

	void save(Class<? extends Config> config);

	void save(Path path, Class<? extends Config> config);
}
