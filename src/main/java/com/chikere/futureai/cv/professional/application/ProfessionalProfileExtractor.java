package com.chikere.futureai.cv.professional.application;

/** Information extraction only. Implementations must never assess AI impact or employment risk. */
public interface ProfessionalProfileExtractor {
    ProfessionalProfile extract(String cvText);

    /** Recorded with every CV-based assessment so a result can be traced to the extractor that produced it. */
    String version();
}
