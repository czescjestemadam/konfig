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

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
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

	@SuppressWarnings("unchecked")
	protected void loadData(Class<?> configClass, Object config, Map<?, ?> data) {
		for (final Field field : configClass.getFields()) {
			final String key = getKey(field);

			final Class<?> fieldType = field.getType();
			if (isSectionClass(fieldType)) {
				if (!data.containsKey(key)) {
					continue;
				}

				if (!(data.get(key) instanceof Map<?, ?> sectionData)) {
					throw new IllegalStateException("Config data needed for " + fieldType + " is invalid");
				}

				try {
					loadData(fieldType, field.get(config), sectionData);
				} catch (final IllegalAccessException e) {
					throw new ConfigLoadException("Failed to get section object " + field, e);
				}
				continue;
			}

			Object value = data.get(key);

			if (
				Map.class.isAssignableFrom(fieldType) &&
					field.getGenericType() instanceof ParameterizedType paramType
			) {
				final Type[] typeArgs = paramType.getActualTypeArguments();
				if (
					typeArgs.length == 2 &&
						typeArgs[0] instanceof Class<?> keyClass &&
						typeArgs[1] instanceof Class<?> valueClass
				) {
					final ValueSerializer<?> keySerializer = getMappings().get(keyClass);
					final ValueSerializer<?> valueSerializer = getMappings().get(valueClass);

					if ((keySerializer != null || valueSerializer != null) && value instanceof Map<?, ?> valueMap) {
						final Map<Object, Object> deserializedMap = new LinkedHashMap<>();

						for (final Map.Entry<?, ?> entry : valueMap.entrySet()) {
							final Object deserializedKey = keySerializer != null
								? keySerializer.deserialize(entry.getKey())
								: entry.getKey();
							final Object deserializedValue = valueSerializer != null
								? valueSerializer.deserialize(entry.getValue())
								: entry.getValue();

							deserializedMap.put(deserializedKey, deserializedValue);
						}

						value = deserializedMap;
					}
				}
			}

			if (
				Collection.class.isAssignableFrom(fieldType) &&
					field.getGenericType() instanceof ParameterizedType paramType
			) {
				final Type[] typeArgs = paramType.getActualTypeArguments();
				if (
					typeArgs.length == 1 &&
						typeArgs[0] instanceof Class<?> elementClass
				) {
					final ValueSerializer<Object> serializer = (ValueSerializer<Object>)getMappings()
						.get(elementClass);

					if (serializer != null && value instanceof Collection<?> valueCollection) {
						final Collection<Object> deserializedCollection = fieldType == List.class
							? new ArrayList<>()
							: new LinkedHashSet<>();

						for (final Object element : valueCollection) {
							deserializedCollection.add(serializer.deserialize(element));
						}

						value = deserializedCollection;
					}
				}
			}

			if (getMappings().containsKey(fieldType)) {
				value = getMappings().get(fieldType).deserialize(value);
			}

			try {
				field.set(config, value);
			} catch (final IllegalAccessException e) {
				throw new ConfigLoadException("Failed to load config value into field " + field, e);
			}
		}

		if (config == null) {
			loadStaticData(configClass, data);
		}
	}

	@SuppressWarnings("unchecked")
	protected Map<Object, Object> saveData(Class<?> configClass, Object config) {
		final Map<Object, Object> data = new LinkedHashMap<>();

		for (final Field field : configClass.getFields()) {
			final String key = getKey(field);
			Object fieldValue;
			try {
				fieldValue = field.get(config);
			} catch (final IllegalAccessException e) {
				throw new ConfigSaveException("Failed to get field value " + field, e);
			}
			final Class<?> fieldType = field.getType();

			if (isSectionClass(fieldType)) {
				final Map<Object, Object> sectionData = saveData(fieldType, fieldValue);

				final String[] comments = getSectionComments(field);
				final Object value = comments != null ?
					new ConfigNode(sectionData, comments) :
					sectionData;
				data.put(key, value);
				continue;
			}

			if (
				Map.class.isAssignableFrom(fieldType) &&
					field.getGenericType() instanceof ParameterizedType paramType
			) {
				final Type[] typeArgs = paramType.getActualTypeArguments();

				if (
					typeArgs.length == 2 &&
						typeArgs[0] instanceof Class<?> keyClass &&
						typeArgs[1] instanceof Class<?> valueClass
				) {
					final ValueSerializer<Object> keySerializer = (ValueSerializer<Object>)getMappings()
						.get(keyClass);
					final ValueSerializer<Object> valueSerializer = (ValueSerializer<Object>)getMappings()
						.get(valueClass);

					if ((keySerializer != null || valueSerializer != null) && fieldValue instanceof Map<?, ?> valueMap) {
						final Map<Object, Object> serializedMap = new LinkedHashMap<>();

						for (final Map.Entry<?, ?> entry : valueMap.entrySet()) {
							final Object serializedKey = keySerializer != null
								? keySerializer.serialize(entry.getKey())
								: entry.getKey();
							final Object serializedValue = valueSerializer != null
								? valueSerializer.serialize(entry.getValue())
								: entry.getValue();

							serializedMap.put(
								serializedKey,
								serializedValue
							);
						}

						fieldValue = serializedMap;
					}
				}
			}

			if (
				Collection.class.isAssignableFrom(fieldType) &&
					field.getGenericType() instanceof ParameterizedType paramType
			) {
				final Type[] typeArgs = paramType.getActualTypeArguments();
				if (
					typeArgs.length == 1 &&
						typeArgs[0] instanceof Class<?> elementClass
				) {
					final ValueSerializer<Object> serializer = (ValueSerializer<Object>)getMappings()
						.get(elementClass);

					if (serializer != null && fieldValue instanceof Collection<?> valueCollection) {
						final Collection<Object> serializedCollection = new ArrayList<>();

						for (final Object element : valueCollection) {
							serializedCollection.add(serializer.serialize(element));
						}

						fieldValue = serializedCollection;
					}
				}
			}

			if (getMappings().containsKey(fieldType)) {
				final ValueSerializer<Object> serializer = (ValueSerializer<Object>)getMappings().get(fieldType);
				fieldValue = serializer.serialize(fieldValue);
			}

			final Comment commentAnnotation = field.getAnnotation(Comment.class);
			data.put(
				key,
				commentAnnotation != null ?
					new ConfigNode(fieldValue, commentAnnotation.value()) :
					fieldValue
			);
		}

		if (config == null) {
			saveStaticData(configClass, data);
		}

		return data;
	}

	private void loadStaticData(Class<?> configClass, Map<?, ?> data) {
		for (final Class<?> subclass : configClass.getClasses()) {
			if (!isSectionClass(subclass)) {
				continue;
			}

			final String key = getStaticSectionKey(subclass);
			if (!data.containsKey(key)) {
				continue;
			}

			if (!(data.get(key) instanceof Map<?, ?> sectionData)) {
				throw new IllegalStateException("Config data needed for " + subclass + " is invalid");
			}

			loadData(subclass, null, sectionData);
		}
	}

	private void saveStaticData(Class<?> configClass, Map<Object, Object> data) {
		for (final Class<?> subclass : configClass.getClasses()) {
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
