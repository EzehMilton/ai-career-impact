package com.chikere.futureai.judgment.typesafe;

/**
 * A System One response that does not answer exactly the questions asked with valid values. The judgment fails; no
 * partial or repaired answer set is ever produced.
 */
public final class InvalidJevResponseException extends RuntimeException {

    public InvalidJevResponseException(String message) {
        super(message);
    }
}
