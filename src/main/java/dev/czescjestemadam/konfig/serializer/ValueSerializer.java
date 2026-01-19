package dev.czescjestemadam.konfig.serializer;

public interface ValueSerializer<T> {
	Object serialize(T value);

	T deserialize(Object value);
}
