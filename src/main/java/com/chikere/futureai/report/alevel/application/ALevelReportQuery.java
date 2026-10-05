package com.chikere.futureai.report.alevel.application;

import java.util.Optional;
import java.util.UUID;

public interface ALevelReportQuery {
    Optional<ALevelReport> assemble(UUID assessmentId, ALevelReportTier tier);
}
