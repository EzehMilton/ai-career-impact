package com.chikere.futureai.configuration;

/**
 * Thrown during startup when the application is configured in a way that cannot be run safely.
 *
 * <p>The message names the setting that is wrong and never its value. Configuration problems are
 * reported into logs that get pasted into tickets and chat, so a message that quotes a key is a
 * message that leaks one.</p>
 */
public class ConfigurationProblemException extends RuntimeException {

    public ConfigurationProblemException(String message) {
        super(message);
    }
}
