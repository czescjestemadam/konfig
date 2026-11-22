package dev.czescjestemadam.konfig.loaders;

import dev.czescjestemadam.konfig.Config;
import dev.czescjestemadam.konfig.annotations.Comment;
import dev.czescjestemadam.konfig.annotations.ConfigKey;
import dev.czescjestemadam.konfig.annotations.ConfigPath;
import dev.czescjestemadam.konfig.exceptions.ConfigLoadException;
import dev.czescjestemadam.konfig.exceptions.ConfigSaveException;
import dev.czescjestemadam.konfig.serializer.SerializerMappings;
import dev.czescjestemadam.konfig.serializer.ValueSerializer;
import dev.czescjestemadam.konfig.utils.Str;

import java.lang.reflect.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.*;

public abstract class AbstractConfigLoader implements ConfigLoader {
	protected final SerializerMappings mappings;

	protected AbstractConfigLoader(SerializerMappings mappings) {
		this.mappings = mappings;
	}

	@Override
	public void load(Config config) {
		load(getAnnotatedConfigPath(config.getClass()), config.getClass(), config);
	}

	@Override
	public void load(Path path, Config config) {
		load(path, config.getClass(), config);
	}

	@Override
	public void load(Class<? extends Config> config) {
		load(getAnnotatedConfigPath(config), config, null);
	}

	@Override
	public void load(Path path, Class<? extends Config> config) {
		load(path, config, null);
	}

	@Override
	public void save(Config config) {
		save(getAnnotatedConfigPath(config.getClass()), config.getClass(), config);
	}

	@Override
	public void save(Path path, Config config) {
		save(path, config.getClass(), config);
	}

	@Override
	public void save(Class<? extends Config> config) {
		save(getAnnotatedConfigPath(config), config, null);
	}

	@Override
	public void save(Path path, Class<? extends Config> config) {
		save(path, config, null);
	}

	public SerializerMappings getMappings() {
		return mappings;
	}

	protected abstract void load(Path path, Class<? extends Config> configClass, Config config);

	protected abstract void save(Path path, Class<? extends Config> configClass, Config config);

	protected void loadData(Class<?> configClass, Object config, Map<?, ?> data) {
		for (final Field field : configClass.getFields()) {
			final String key = getKey(field);
			if (!data.containsKey(key)) {
				continue;
			}

			final Object rawValue = data.get(key);
			final Class<?> fieldType = field.getType();

			if (isSectionClass(fieldType)) {
				if (rawValue == null) {
					continue;
				}

				if (!(rawValue instanceof Map<?, ?> sectionData)) {
					throw new ConfigLoadException("Config data needed for " + fieldType + " is invalid");
				}

				loadData(fieldType, getOrCreateSection(field, config), sectionData);
				continue;
			}

			final Object value;
			try {
				value = deserializeValue(field.getGenericType(), rawValue);
			} catch (final RuntimeException e) {
				throw new ConfigLoadException("Failed to deserialize value for field " + field, e);
			}

			if (value == null && fieldType.isPrimitive()) {
				continue;
			}

			try {
				field.set(config, value);
			} catch (final IllegalAccessException | IllegalArgumentException e) {
				throw new ConfigLoadException("Failed to load config value into field " + field, e);
			}
		}

		if (config == null) {
			loadStaticData(configClass, data);
		}
	}

	protected Map<Object, Object> saveData(Class<?> configClass, Object config) {
		final Map<Object, Object> data = new LinkedHashMap<>();

		for (final Field field : configClass.getFields()) {
			final String key = getKey(field);
			final Object fieldValue;
			try {
				fieldValue = field.get(config);
			} catch (final IllegalAccessException e) {
				throw new ConfigSaveException("Failed to get field value " + field, e);
			}

			final Class<?> fieldType = field.getType();

			if (isSectionClass(fieldType)) {
				final Object sectionData = fieldValue != null
					? saveData(fieldType, fieldValue)
					: null;

				final String[] comments = getSectionComments(field);
				data.put(key, comments != null ? new ConfigNode(sectionData, comments) : sectionData);
				continue;
			}

			final Object value;
			try {
				value = serializeValue(field.getGenericType(), fieldValue);
			} catch (final RuntimeException e) {
				throw new ConfigSaveException("Failed to serialize value of field " + field, e);
			}

			final Comment commentAnnotation = field.getAnnotation(Comment.class);
			data.put(
				key,
				commentAnnotation != null
					? new ConfigNode(value, commentAnnotation.value())
					: value
			);
		}

		if (config == null) {
			saveStaticData(configClass, data);
		}

		return data;
	}

	protected Object deserializeValue(Type type, Object value) {
		if (value == null) {
			return null;
		}

		final Class<?> rawType = getRawType(type);

		final ValueSerializer<?> serializer = findSerializer(type, rawType);
		if (serializer != null) {
			return serializer.deserialize(value);
		}

		if (Map.class.isAssignableFrom(rawType) && value instanceof Map<?, ?> valueMap) {
			final Type keyType = getTypeArgument(type, 0, 2);
			final Type valueType = getTypeArgument(type, 1, 2);

			final Map<Object, Object> map = createMap(rawType);
			for (final Map.Entry<?, ?> entry : valueMap.entrySet()) {
				map.put(
					deserializeValue(keyType, entry.getKey()),
					deserializeValue(valueType, entry.getValue())
				);
			}
			return map;
		}

		if (Collection.class.isAssignableFrom(rawType) && value instanceof Collection<?> valueCollection) {
			final Type elementType = getTypeArgument(type, 0, 1);

			final Collection<Object> collection = createCollection(rawType);
			for (final Object element : valueCollection) {
				collection.add(deserializeValue(elementType, element));
			}
			return collection;
		}

		if (value instanceof Number number) {
			final Object converted = convertNumber(rawType, number);
			if (converted != null) {
				return converted;
			}
		}

		return value;
	}

	@SuppressWarnings("unchecked")
	protected Object serializeValue(Type type, Object value) {
		if (value == null) {
			return null;
		}

		ValueSerializer<Object> serializer = (ValueSerializer<Object>)findSerializer(type, getRawType(type));
		if (serializer == null) {
			serializer = (ValueSerializer<Object>)getMappings().get(value.getClass());
		}
		if (serializer != null) {
			return serializer.serialize(value);
		}

		if (value instanceof Map<?, ?> valueMap) {
			final Type keyType = getTypeArgument(type, 0, 2);
			final Type valueType = getTypeArgument(type, 1, 2);

			final Map<Object, Object> map = new LinkedHashMap<>();
			for (final Map.Entry<?, ?> entry : valueMap.entrySet()) {
				map.put(
					serializeValue(keyType, entry.getKey()),
					serializeValue(valueType, entry.getValue())
				);
			}
			return map;
		}

		if (value instanceof Collection<?> valueCollection) {
			final Type elementType = getTypeArgument(type, 0, 1);

			final List<Object> list = new ArrayList<>();
			for (final Object element : valueCollection) {
				list.add(serializeValue(elementType, element));
			}
			return list;
		}

		return value;
	}

	private ValueSerializer<?> findSerializer(Type type, Class<?> rawType) {
		final ValueSerializer<?> serializer = getMappings().get(type);
		return serializer != null ? serializer : getMappings().get(rawType);
	}

	private static Class<?> getRawType(Type type) {
		if (type instanceof Class<?> cls) {
			return cls;
		}
		if (type instanceof ParameterizedType paramType && paramType.getRawType() instanceof Class<?> cls) {
			return cls;
		}
		if (type instanceof WildcardType wildcardType) {
			final Type[] upperBounds = wildcardType.getUpperBounds();
			return upperBounds.length > 0 ? getRawType(upperBounds[0]) : Object.class;
		}
		if (type instanceof TypeVariable<?> typeVariable) {
			final Type[] bounds = typeVariable.getBounds();
			return bounds.length > 0 ? getRawType(bounds[0]) : Object.class;
		}
		if (type instanceof GenericArrayType) {
			return Object[].class;
		}
		return Object.class;
	}

	private static Type getTypeArgument(Type type, int index, int expectedCount) {
		if (type instanceof ParameterizedType paramType) {
			final Type[] typeArgs = paramType.getActualTypeArguments();
			if (typeArgs.length == expectedCount) {
				return typeArgs[index];
			}
		}
		return Object.class;
	}

	@SuppressWarnings("unchecked")
	private static Map<Object, Object> createMap(Class<?> rawType) {
		if (!rawType.isInterface() && !Modifier.isAbstract(rawType.getModifiers())) {
			final Object instance = tryInstantiate(rawType);
			if (instance != null) {
				return (Map<Object, Object>)instance;
			}
		}
		if (SortedMap.class.isAssignableFrom(rawType)) {
			return new TreeMap<>();
		}
		return new LinkedHashMap<>();
	}

	@SuppressWarnings("unchecked")
	private static Collection<Object> createCollection(Class<?> rawType) {
		if (!rawType.isInterface() && !Modifier.isAbstract(rawType.getModifiers())) {
			final Object instance = tryInstantiate(rawType);
			if (instance != null) {
				return (Collection<Object>)instance;
			}
		}
		if (SortedSet.class.isAssignableFrom(rawType)) {
			return new TreeSet<>();
		}
		if (Set.class.isAssignableFrom(rawType)) {
			return new LinkedHashSet<>();
		}
		if (Queue.class.isAssignableFrom(rawType)) {
			return new ArrayDeque<>();
		}
		return new ArrayList<>();
	}

	private static Object tryInstantiate(Class<?> cls) {
		try {
			return cls.getConstructor().newInstance();
		} catch (final ReflectiveOperationException e) {
			return null;
		}
	}

	private static Object convertNumber(Class<?> type, Number number) {
		if (type == int.class || type == Integer.class) {
			return number.intValue();
		}
		if (type == long.class || type == Long.class) {
			return number.longValue();
		}
		if (type == double.class || type == Double.class) {
			return number.doubleValue();
		}
		if (type == float.class || type == Float.class) {
			return number.floatValue();
		}
		if (type == short.class || type == Short.class) {
			return number.shortValue();
		}
		if (type == byte.class || type == Byte.class) {
			return number.byteValue();
		}
		if (type == BigInteger.class) {
			return number instanceof BigInteger bigInteger ? bigInteger : BigInteger.valueOf(number.longValue());
		}
		if (type == BigDecimal.class) {
			return number instanceof BigDecimal bigDecimal ? bigDecimal : new BigDecimal(number.toString());
		}
		return null;
	}

	private static Object getOrCreateSection(Field field, Object config) {
		try {
			final Object section = field.get(config);
			if (section != null) {
				return section;
			}

			final Object created = field.getType().getConstructor().newInstance();
			field.set(config, created);
			return created;
		} catch (final ReflectiveOperationException e) {
			throw new ConfigLoadException("Failed to get or create section object " + field, e);
		}
	}

	private void loadStaticData(Class<?> configClass, Map<?, ?> data) {
		for (final Class<?> subclass : configClass.getClasses()) {
			if (!isSectionClass(subclass)) {
				continue;
			}

			final String key = getStaticSectionKey(subclass);
			final Object rawSectionData = data.get(key);
			if (rawSectionData == null) {
				continue;
			}

			if (!(rawSectionData instanceof Map<?, ?> sectionData)) {
				throw new ConfigLoadException("Config data needed for " + subclass + " is invalid");
			}

			loadData(subclass, null, sectionData);
		}
	}

	private void saveStaticData(Class<?> configClass, Map<Object, Object> data) {
		final Class<?>[] subclasses = configClass.getClasses();
		Arrays.sort(subclasses, Comparator.comparing(AbstractConfigLoader::getStaticSectionKey));

		for (final Class<?> subclass : subclasses) {
			if (!isSectionClass(subclass)) {
				continue;
			}

			final String key = getStaticSectionKey(subclass);
			final Map<Object, Object> sectionData = saveData(subclass, null);

			final String[] comments = getStaticSectionComments(subclass);
			data.put(key, comments != null ? new ConfigNode(sectionData, comments) : sectionData);
		}
	}

	private static String getKey(Field field) {
		final ConfigKey key = field.getAnnotation(ConfigKey.class);
		return key != null ? key.value() : Str.kebabCase(field.getName());
	}

	private static String getStaticSectionKey(Class<?> cls) {
		final ConfigKey key = cls.getAnnotation(ConfigKey.class);
		return key != null ? key.value() : Str.kebabCase(cls.getSimpleName());
	}

	private static boolean isSectionClass(Class<?> cls) {
		return Config.class.isAssignableFrom(cls);
	}

	private static String[] getSectionComments(Field sectionField) {
		final List<String> comments = new ArrayList<>();

		final String[] staticSectionComments = getStaticSectionComments(sectionField.getType());
		if (staticSectionComments != null) {
			comments.addAll(List.of(staticSectionComments));
		}

		final Comment comment = sectionField.getAnnotation(Comment.class);
		if (comment != null) {
			comments.addAll(List.of(comment.value()));
		}

		return comments.isEmpty() ? null : comments.toArray(String[]::new);
	}

	private static String[] getStaticSectionComments(Class<?> cls) {
		final Comment comment = cls.getAnnotation(Comment.class);
		return comment != null ? comment.value() : null;
	}

	private static Path getAnnotatedConfigPath(Class<? extends Config> config) {
		final ConfigPath annotation = config.getAnnotation(ConfigPath.class);
		if (annotation == null) {
			throw new IllegalArgumentException("@ConfigPath annotation is missing on " + config);
		}

		return Path.of(annotation.value());
	}
}
