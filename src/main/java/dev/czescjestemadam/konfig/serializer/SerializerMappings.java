package dev.czescjestemadam.konfig.serializer;

import java.lang.reflect.Type;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class SerializerMappings extends AbstractMap<Type, ValueSerializer<?>> {
	private final Map<Type, ValueSerializer<?>> serializers = new HashMap<>();

	public SerializerMappings() {
		this(Map.of());
	}

	public SerializerMappings(Map<Type, ValueSerializer<?>> serializers) {
		this.serializers.putAll(serializers);
	}

	public SerializerMappings with(Type type, ValueSerializer<?> serializer) {
		serializers.put(type, serializer);
		return this;
	}

	@Override
	public Set<Entry<Type, ValueSerializer<?>>> entrySet() {
		return serializers.entrySet();
	}
}
