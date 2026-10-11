package com.chikere.futureai.cv.professional.application;

/** One dated job as written in the CV; dates are raw text and are parsed only by Java. */
public record ProfessionalEmploymentEntry(
        String title, String employer, String start, String end, boolean relevantToCurrentRole) {

    public ProfessionalEmploymentEntry {
        title = text(title);
        employer = text(employer);
        start = text(start);
        end = text(end);
    }

    private static String text(String value) {
        return value == null ? "" : value.strip();
    }
}
