package dev.czescjestemadam.konfig.tests;

import dev.czescjestemadam.konfig.serializer.AbstractStringValueSerializer;
import dev.czescjestemadam.konfig.serializer.ValueSerializer;

public class ExampleSerializable {
	public String str;

	ExampleSerializable(String str) {
		this.str = str;
	}

	static final ValueSerializer<ExampleSerializable> SERIALIZER = new AbstractStringValueSerializer<>() {
		@Override
		public Object serialize(ExampleSerializable value) {
			return value.str;
		}

		@Override
		public ExampleSerializable deserialize(Object value) {
			return new ExampleSerializable((String)value);
		}
	};
}
