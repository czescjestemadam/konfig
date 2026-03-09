package dev.czescjestemadam.konfig.tests;

import dev.czescjestemadam.konfig.Config;
import dev.czescjestemadam.konfig.annotations.Comment;
import dev.czescjestemadam.konfig.annotations.ConfigPath;
import dev.czescjestemadam.konfig.loaders.ConfigLoader;
import dev.czescjestemadam.konfig.loaders.yaml.YamlConfigLoader;
import dev.czescjestemadam.konfig.serializer.SerializerMappings;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class YamlTest {
	private static final String YAML_PATH = "src/test/resources/test.yaml";

	private static final SerializerMappings MAPPINGS = new SerializerMappings();
	private static final ConfigLoader LOADER = new YamlConfigLoader(MAPPINGS);

	@Test
	void load() {
		final TestConfig config = new TestConfig();
		LOADER.load(config);

		assertEquals(123, config.number);
		assertEquals("abc", config.str);
		assertEquals(456, config.section.number);
		assertEquals("def", config.section.str);
		assertEquals("test", config.commentedSection.value);
	}

	@Test
	void save() throws IOException {
		final TestConfig config = new TestConfig();
		final Path path = Path.of("src/test/resources/saved.yaml");
		LOADER.save(path, config);

		assertEquals(
			"number: 0\nstr: str\nsection:\n  number: 1\n  str: str2\n#section comment\ncommented-section:\n  #value comment\n  value: v\n",
			Files.readString(path)
		);
	}

	@Test
	void yamlLoadStatic() {
		LOADER.load(StaticTestConfig.class);

		assertEquals(123, StaticTestConfig.number);
		assertEquals("abc", StaticTestConfig.str);
		assertEquals(456, StaticTestConfig.Section.number);
		assertEquals("def", StaticTestConfig.Section.str);
		assertEquals("test", StaticTestConfig.CommentedSection.value);
	}

	@Test
	void yamlSaveStatic() {
		final Path path = Path.of("src/test/resources/saved-static.yaml");
		LOADER.save(path, StaticTestConfig.class);
	}


	@Test
	void loadSerializable() {
		MAPPINGS.with(ExampleSerializable.class, ExampleSerializable.SERIALIZER);
		assertNotNull(MAPPINGS.get(ExampleSerializable.class));

		final SerializableTestConfig config = new SerializableTestConfig();

		LOADER.load(config);

		assertEquals("abc", config.str.str);
		assertEquals(1, config.map.size());
		assertEquals("321", config.map.get(2).str);
	}

	@Test
	void saveSerializable() throws IOException {
		MAPPINGS.with(ExampleSerializable.class, ExampleSerializable.SERIALIZER);
		assertNotNull(MAPPINGS.get(ExampleSerializable.class));

		final SerializableTestConfig config = new SerializableTestConfig();

		final Path path = Path.of("src/test/resources/saved-serializable-test.yaml");
		LOADER.save(path, config);

		assertEquals("str: '123123123'\nmap:\n  1: '123'\n", Files.readString(path));
	}

	@ConfigPath(YAML_PATH)
	public static class TestConfig implements Config {
		public int number = 0;
		public String str = "str";
		public Section section = new Section();
		public CommentedSection commentedSection = new CommentedSection();

		public static class Section implements Config {
			public int number = 1;
			public String str = "str2";
		}

		@Comment("section comment")
		public static class CommentedSection implements Config {
			@Comment("value comment")
			public String value = "v";
		}
	}

	@ConfigPath(YAML_PATH)
	public static class StaticTestConfig implements Config {
		public static int number;
		public static String str;

		public static class Section implements Config {
			public static int number;
			public static String str;
		}

		@Comment("section comment")
		public static class CommentedSection implements Config {
			@Comment("value comment")
			public static String value;
		}
	}

	@ConfigPath(YAML_PATH)
	public static class SerializableTestConfig implements Config {
		public ExampleSerializable str = new ExampleSerializable("123123123");

		public Map<Integer, ExampleSerializable> map = Map.of(1, new ExampleSerializable("123"));
	}
}
