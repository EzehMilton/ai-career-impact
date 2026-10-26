package com.chikere.futureai.livesmoke;

import java.util.List;

/**
 * What this run is actually wired to, read from the provider beans themselves rather than from configuration
 * text, so the dashboard shows the truth. Contains no credential, key, token or password.
 */
public record LiveSmokeProviderStatus(List<Row> rows) {

    public LiveSmokeProviderStatus {
        rows = List.copyOf(rows);
    }

    /** @param ok whether this is what a live smoke run should show */
    public record Row(String name, String value, String detail, boolean ok) { }
}
