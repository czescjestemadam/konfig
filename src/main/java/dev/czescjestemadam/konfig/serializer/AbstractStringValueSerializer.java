package dev.czescjestemadam.konfig.serializer;

public abstract class AbstractStringValueSerializer<T> implements ValueSerializer<T> {
	@Override
	public Object serialize(T value) {
		return value != null
			? value.toString()
			: null;
	}
}
