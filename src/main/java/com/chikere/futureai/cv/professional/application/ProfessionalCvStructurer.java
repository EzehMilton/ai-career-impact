package com.chikere.futureai.cv.professional.application;

/** Structures already-extracted CV text. It must never assess AI impact. */
public interface ProfessionalCvStructurer {
    ProfessionalCvParseResult structure(String extractedText);
}
