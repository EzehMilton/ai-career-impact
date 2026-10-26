package com.chikere.futureai.livesmoke;

import java.util.Map;
import java.util.Objects;

/**
 * One known input for a journey's normal form.
 *
 * @param id     stable identifier used in the prefill URL
 * @param source where the inputs come from, so no case is mistaken for invented data
 * @param fields form property name to submitted text, exactly as a visitor would type it; list fields use one
 *               line per item, as the normal form does
 */
public record LiveSmokeCase(String id, LiveSmokeJourney journey, String label, String source,
        Map<String, Object> fields) {

    public LiveSmokeCase {
        Objects.requireNonNull(id);
        Objects.requireNonNull(journey);
        Objects.requireNonNull(label);
        Objects.requireNonNull(source);
        fields = Map.copyOf(fields);
        if (!id.matches("[a-z0-9-]+")) {
            throw new IllegalArgumentException("smoke case id must be lower-case letters, digits and hyphens: " + id);
        }
        if (id.equals("new")) {
            throw new IllegalArgumentException("'new' is reserved for the custom assessment route");
        }
    }
}
