package com.chikere.futureai.coursemodule.university.policy;

/** Safeguard flags carried alongside a module's primary category; never categories themselves. */
public enum UniversityModuleFlag {
    /**
     * AI may support learning or preparation, but demonstrating the student's own capability is especially important.
     * It never states or implies that AI use is permitted in assessed work.
     */
    INDEPENDENT_DEMONSTRATION_SENSITIVE,
    /** The module appears materially shaped by professional, regulated, safety, ethical or accreditation standards. */
    PROFESSIONAL_STANDARDS_RELEVANT
}
