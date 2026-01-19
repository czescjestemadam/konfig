package dev.czescjestemadam.konfig.exceptions;

public class ConfigLoaderException extends ConfigException {
	public ConfigLoaderException(String message) {
		super(message);
	}

	public ConfigLoaderException(String message, Throwable cause) {
		super(message, cause);
	}
}
