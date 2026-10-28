package com.chikere.futureai.report.alevel.detailed;

import java.util.Objects;

/** The detailed report cannot be prepared for this request. No provider was called and nothing was stored. */
public final class ALevelDetailedReportUnavailableException extends RuntimeException {

    public enum Reason {
        /** No ACTIVE a-level-report-v2 entitlement for the assessment. */
        NOT_ENTITLED,
        /** No a-level-decision-v2 assessment with that id, or one that needed more information. */
        ASSESSMENT_NOT_FOUND
    }

    private final Reason reason;

    public ALevelDetailedReportUnavailableException(Reason reason, String message) {
        super(message);
        this.reason = Objects.requireNonNull(reason, "reason must not be null");
    }

    public Reason reason() {
        return reason;
    }
}
