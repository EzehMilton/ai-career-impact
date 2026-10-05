package com.chikere.futureai.cv.professional.application;

public interface ProfessionalCvParser {
    /** Extracts text only. It must not interpret roles, responsibilities, skills or experience. */
    ProfessionalCvText parse(ProfessionalCvDocument document);
}
