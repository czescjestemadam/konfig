package dev.czescjestemadam.konfig.exceptions;

public class ConfigSaveException extends ConfigException {
	public ConfigSaveException(String message) {
		super(message);
	}

	public ConfigSaveException(String message, Throwable cause) {
		super(message, cause);
	}
}
