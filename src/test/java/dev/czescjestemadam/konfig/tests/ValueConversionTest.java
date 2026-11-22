package dev.czescjestemadam.konfig.tests;

import dev.czescjestemadam.konfig.Config;
import dev.czescjestemadam.konfig.exceptions.ConfigLoadException;
import dev.czescjestemadam.konfig.loaders.ConfigLoader;
import dev.czescjestemadam.konfig.loaders.yaml.YamlConfigLoader;
import dev.czescjestemadam.konfig.serializer.SerializerMappings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ValueConversionTest {
	private static final ConfigLoader LOADER = new YamlConfigLoader(
		new SerializerMappings().with(ExampleSerializable.class, ExampleSerializable.SERIALIZER)
	);

	@TempDir
	Path dir;

	private <T extends Config> T load(String yaml, T config) throws IOException {
		final Path path = dir.resolve("config.yaml");
		Files.writeString(path, yaml);
		LOADER.load(path, config);
		return config;
	}

	private String save(Config config) throws IOException {
		final Path path = dir.resolve("config.yaml");
		LOADER.save(path, config);
		return Files.readString(path);
	}

	@Test
	void loadsNumbersIntoWiderOrNarrowerTypes() throws IOException {
		final NumbersConfig config = load(
			"l: 1\nd: 2\nf: 3\ns: 4\nb: 5\nboxed: 6\nbig: 7\nkeep: null\n",
			new NumbersConfig()
		);

		assertEquals(1L, config.l);
		assertEquals(2.0, config.d);
		assertEquals(3.0f, config.f);
		assertEquals((short)4, config.s);
		assertEquals((byte)5, config.b);
		assertEquals(6L, config.boxed);
		assertEquals(new BigDecimal("7"), config.big);
		assertEquals(42, config.keep, "null must not overwrite a primitive's default");
	}

	@Test
	void loadsSetsAndSortedCollections() throws IOException {
		final CollectionsConfig config = load(
			"set: [b, a, b]\nsorted: [c, a, b]\nlinked: [x]\n",
			new CollectionsConfig()
		);

		assertEquals(Set.of("a", "b"), config.set);
		assertEquals(List.of("a", "b", "c"), new ArrayList<>(config.sorted));
		assertInstanceOf(LinkedList.class, config.linked);
	}

	@Test
	void handlesNestedGenerics() throws IOException {
		final NestedGenericsConfig config = load(
			"map:\n  k: ['1', '2']\nlists:\n- ['3']\n",
			new NestedGenericsConfig()
		);

		assertEquals("2", config.map.get("k").get(1).str);
		assertEquals("3", config.lists.get(0).get(0).str);

		assertEquals("map:\n  k:\n  - '1'\n  - '2'\nlists:\n- - '3'\n", save(config));
	}

	@Test
	void savesNullsInsideCollectionsAndMaps() throws IOException {
		final NullsConfig config = new NullsConfig();
		config.list = new ArrayList<>(Arrays.asList("a", null));
		config.map = new LinkedHashMap<>();
		config.map.put("k", null);

		assertEquals("list:\n- a\n- null\nmap:\n  k: null\n", save(config));
	}

	@Test
	void createsNullSectionOnLoadAndSavesItAsNull() throws IOException {
		final NullSectionConfig config = load("section:\n  value: 5\n", new NullSectionConfig());
		assertNotNull(config.section);
		assertEquals(5, config.section.value);

		assertEquals("section: null\n", save(new NullSectionConfig()));
		assertNull(load("section: null\n", new NullSectionConfig()).section);
	}

	@Test
	void loadsEmptyFile() throws IOException {
		assertEquals(42, load("", new NumbersConfig()).keep);
	}

	@Test
	void rejectsNonMappingRoot() {
		assertThrows(ConfigLoadException.class, () -> load("- a\n- b\n", new NumbersConfig()));
	}

	@Test
	void savesAtomicallyWithoutLeavingTempFile() throws IOException {
		save(new NumbersConfig());
		try (var files = Files.list(dir)) {
			assertEquals(List.of(dir.resolve("config.yaml")), files.toList());
		}
	}

	@Test
	void savesToBareFileName() throws IOException {
		final Path path = Path.of("konfig-bare-path-test.yaml");
		try {
			LOADER.save(path, new NumbersConfig());
			assertTrue(Files.exists(path));
		} finally {
			Files.deleteIfExists(path);
		}
	}

	public static class NumbersConfig implements Config {
		public long l;
		public double d;
		public float f;
		public short s;
		public byte b;
		public Long boxed;
		public BigDecimal big;
		public int keep = 42;
	}

	public static class CollectionsConfig implements Config {
		public Set<String> set;
		public SortedSet<String> sorted;
		public LinkedList<String> linked;
	}

	public static class NestedGenericsConfig implements Config {
		public Map<String, List<ExampleSerializable>> map;
		public List<List<ExampleSerializable>> lists;
	}

	public static class NullsConfig implements Config {
		public List<String> list;
		public Map<String, String> map;
	}

	public static class NullSectionConfig implements Config {
		public Section section;

		public static class Section implements Config {
			public int value;
		}
	}
}
