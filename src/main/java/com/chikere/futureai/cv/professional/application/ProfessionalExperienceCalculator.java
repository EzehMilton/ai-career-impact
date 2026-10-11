package com.chikere.futureai.cv.professional.application;

import java.time.Clock;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Calculates years of relevant experience from the CV's own employment dates. The model only copies
 * dates and says which jobs are relevant; parsing, merging and rounding happen here.
 *
 * <p>Any doubt about a relevant job's dates gives an unknown result rather than a guess.</p>
 */
public final class ProfessionalExperienceCalculator {
    public static final int MAXIMUM_YEARS = 70;
    private static final Pattern NUMERIC = Pattern.compile("(0?[1-9]|1[0-2])[/.-](\\d{4})");
    private static final Pattern NAMED = Pattern.compile("(?i)([a-z]+)\\.?\\s+(\\d{4})");
    private static final Pattern YEAR = Pattern.compile("\\d{4}");
    private static final Pattern ONGOING = Pattern.compile("(?i)present|current|now|to\\s+date|ongoing");
    private static final Map<String, Integer> MONTHS = months();

    private final Clock clock;

    public ProfessionalExperienceCalculator(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * @param history          every dated job the model listed
     * @param currentRoleStart the selected current job's start, as written, used to recognise that job
     * @param currentRoleEnd   the selected current job's end, as written
     * @param sentText         the exact CV text the model saw; relevant dates must appear in it
     */
    public ProfessionalExperience calculate(List<ProfessionalEmploymentEntry> history, String currentRoleStart,
            String currentRoleEnd, String sentText) {
        if (history == null) return ProfessionalExperience.unknown();
        YearMonth now = YearMonth.now(clock);
        String text = ProfessionalCvTextMatching.normalise(sentText);
        String currentStart = ProfessionalCvTextMatching.normalise(currentRoleStart);
        String currentEnd = ProfessionalCvTextMatching.normalise(currentRoleEnd);

        var periods = new ArrayList<Period>();
        var basis = new ArrayList<String>();
        for (var entry : history) {
            if (entry == null || !relevant(entry, currentStart, currentEnd)) continue;
            Optional<YearMonth> start = copied(entry.start(), text)
                    .flatMap(value -> parse(value, false, now));
            Optional<YearMonth> end = ongoing(entry.end()) ? Optional.of(now)
                    : copied(entry.end(), text).flatMap(value -> parse(value, true, now));
            if (start.isEmpty() || end.isEmpty() || start.get().isAfter(now)
                    || end.get().isBefore(start.get())) {
                return ProfessionalExperience.unknown();
            }
            // Time not yet served, such as a contract end date in the future, is not experience.
            periods.add(new Period(start.get(), end.get().isAfter(now) ? now : end.get()));
            basis.add(describe(entry));
        }
        if (periods.isEmpty()) return ProfessionalExperience.unknown();
        long months = mergedMonths(periods);
        int years = (int) (months / 12 + (months % 12 >= 6 ? 1 : 0));
        if (years > MAXIMUM_YEARS) return ProfessionalExperience.unknown();
        return new ProfessionalExperience(OptionalInt.of(years),
                basis.stream().limit(ProfessionalProfile.MAXIMUM_EXPERIENCE_BASIS).toList());
    }

    /** The current job always counts, whatever the model says about its relevance. */
    private boolean relevant(ProfessionalEmploymentEntry entry, String currentStart, String currentEnd) {
        if (entry.relevantToCurrentRole()) return true;
        return !currentStart.isEmpty() && !currentEnd.isEmpty()
                && ProfessionalCvTextMatching.normalise(entry.start()).equals(currentStart)
                && ProfessionalCvTextMatching.normalise(entry.end()).equals(currentEnd);
    }

    /** A date the model did not copy from the text it was given is treated as unparseable. */
    private Optional<String> copied(String date, String text) {
        String normalised = ProfessionalCvTextMatching.normalise(date);
        return !normalised.isEmpty() && text.contains(normalised) ? Optional.of(date.strip()) : Optional.empty();
    }

    private boolean ongoing(String date) {
        return date != null && ONGOING.matcher(date.strip().replaceAll("\\s+", " ")).matches();
    }

    /** A year alone starts in January and ends in December. Present-style words are never a start. */
    private Optional<YearMonth> parse(String date, boolean end, YearMonth now) {
        Matcher numeric = NUMERIC.matcher(date);
        if (numeric.matches()) {
            return Optional.of(YearMonth.of(Integer.parseInt(numeric.group(2)), Integer.parseInt(numeric.group(1))));
        }
        Matcher named = NAMED.matcher(date);
        if (named.matches()) {
            Integer month = MONTHS.get(named.group(1).toLowerCase(Locale.ROOT));
            return month == null ? Optional.empty()
                    : Optional.of(YearMonth.of(Integer.parseInt(named.group(2)), month));
        }
        if (YEAR.matcher(date).matches()) {
            return Optional.of(YearMonth.of(Integer.parseInt(date), end ? 12 : 1));
        }
        return Optional.empty();
    }

    /** Overlapping or touching periods are merged, then months are counted inclusively. */
    private long mergedMonths(List<Period> periods) {
        var sorted = periods.stream().sorted(Comparator.comparing(Period::start)).toList();
        long months = 0;
        YearMonth start = sorted.getFirst().start();
        YearMonth end = sorted.getFirst().end();
        for (var period : sorted.subList(1, sorted.size())) {
            if (!period.start().isAfter(end.plusMonths(1))) {
                if (period.end().isAfter(end)) end = period.end();
            }
            else {
                months += ChronoUnit.MONTHS.between(start, end) + 1;
                start = period.start();
                end = period.end();
            }
        }
        return months + ChronoUnit.MONTHS.between(start, end) + 1;
    }

    private String describe(ProfessionalEmploymentEntry entry) {
        String job = entry.title().isEmpty() ? entry.employer()
                : entry.employer().isEmpty() ? entry.title() : entry.title() + " at " + entry.employer();
        return job + " (" + entry.start() + " – " + entry.end() + ")";
    }

    private static Map<String, Integer> months() {
        String[] names = {"january", "february", "march", "april", "may", "june", "july", "august",
                "september", "october", "november", "december"};
        var months = new HashMap<String, Integer>();
        for (int i = 0; i < names.length; i++) {
            months.put(names[i], i + 1);
            months.put(names[i].substring(0, 3), i + 1);
        }
        months.put("sept", 9);
        return Map.copyOf(months);
    }

    private record Period(YearMonth start, YearMonth end) {}
}
