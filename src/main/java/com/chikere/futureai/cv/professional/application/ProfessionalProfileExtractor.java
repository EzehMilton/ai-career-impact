package com.chikere.futureai.cv.professional.application;

/** Information extraction only. Implementations must never assess AI impact or employment risk. */
public interface ProfessionalProfileExtractor {
    ProfessionalProfile extract(String cvText);
}
