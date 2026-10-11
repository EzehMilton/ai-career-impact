package com.chikere.futureai.cv.professional.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ProfessionalExperienceCalculatorTest {
    private static final Clock OCTOBER_2026 = Clock.fixed(Instant.parse("2026-10-15T09:00:00Z"), ZoneOffset.UTC);
    private final ProfessionalExperienceCalculator calculator = new ProfessionalExperienceCalculator(OCTOBER_2026);

    @Test
    void realCvLayoutCountsOnlyRelatedJobsAndTheirOverlapOnce() {
        String cv = """
                Software Engineer, Push Gaming, 08/2025 – Present
                Software Developer, Sopra Banking Software, 04/2023 – 08/2025
                Security Design Engineer, Buro Happold, 06/2021 – 04/2023
                EOD Technician, US Navy, 03/2009 – 02/2016
                """;
        var history = List.of(
                new ProfessionalEmploymentEntry("Software Engineer", "Push Gaming", "08/2025", "Present", true),
                new ProfessionalEmploymentEntry("Software Developer", "Sopra Banking Software", "04/2023", "08/2025", true),
                new ProfessionalEmploymentEntry("Security Design Engineer", "Buro Happold", "06/2021", "04/2023", false),
                new ProfessionalEmploymentEntry("EOD Technician", "US Navy", "03/2009", "02/2016", false));

        var experience = calculator.calculate(history, "08/2025", "Present", cv);

        // 04/2023 to 10/2026 inclusive is 43 months, which rounds to 4 years.
        assertThat(experience.years()).hasValue(4);
        assertThat(experience.basis()).containsExactly(
                "Software Engineer at Push Gaming (08/2025 – Present)",
                "Software Developer at Sopra Banking Software (04/2023 – 08/2025)");
        assertThat(experience.excluded()).containsExactly(
                "Security Design Engineer at Buro Happold (06/2021 – 04/2023)",
                "EOD Technician at US Navy (03/2009 – 02/2016)");
    }

    @Test
    void excludedJobsAreListedOnlyWhenDatedAndOnlyAlongsideAResult() {
        var history = List.of(job("01/2024", "12/2024", true),
                new ProfessionalEmploymentEntry("Volunteer", "Charity", "", "", false),
                new ProfessionalEmploymentEntry("Barista", "Cafe", "2015", "2016", false));
        assertThat(calculator.calculate(history, "", "", text(history)).excluded())
                .containsExactly("Barista at Cafe (2015 – 2016)");

        var unknown = List.of(job("Spring 2020", "Present", true), job("2015", "2016", false));
        assertThat(calculator.calculate(unknown, "", "", text(unknown)).excluded()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "03/2020, 02/2022", "3/2020, 2/2022", "03.2020, 02.2022", "03-2020, 02-2022",
            "March 2020, February 2022", "Mar 2020, Feb 2022", "mar. 2020, FEB 2022", "MARCH 2020, february 2022",
            "Sept 2020, August 2022", "2020, 2021"})
    void eachAcceptedDateFormatIsParsed(String start, String end) {
        assertThat(years(job(start, end, true))).isEqualTo("2");
    }

    @Test
    void aYearAloneStartsInJanuaryAndEndsInDecember() {
        // January 2019 to December 2019 is exactly 12 months.
        assertThat(years(job("2019", "2019", true))).isEqualTo("1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Present", "present", "Current", "Now", "To date", "to  DATE", "Ongoing"})
    void presentStyleWordsAreTheCurrentMonthAsAnEndDate(String end) {
        // 10/2024 to 10/2026 inclusive is 25 months.
        assertThat(years(job("10/2024", end, true))).isEqualTo("2");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Present", "Current", "Now", "To date", "Ongoing"})
    void presentStyleWordsAreNeverAStartDate(String start) {
        assertThat(years(job(start, "Present", true))).isEqualTo("unknown");
    }

    @Test
    void overlappingJobsAreCountedOnce() {
        // 01/2020 to 12/2023 is 48 months; adding both jobs separately would give 67.
        assertThat(years(job("01/2020", "12/2022", true), job("06/2021", "12/2023", true))).isEqualTo("4");
    }

    @Test
    void touchingJobsAreMergedAndAGapIsNotCounted() {
        assertThat(years(job("01/2020", "12/2020", true), job("01/2021", "12/2021", true))).isEqualTo("2");
        // 24 + 24 months; the three-year gap between them is not experience.
        assertThat(years(job("01/2015", "12/2016", true), job("01/2020", "12/2021", true))).isEqualTo("4");
    }

    @Test
    void irrelevantJobsAreExcludedEvenWhenTheirDatesAreUnparseable() {
        assertThat(years(job("01/2024", "12/2024", true), job("01/2010", "12/2019", false),
                job("Summer 2008", "whenever", false))).isEqualTo("1");
    }

    @Test
    void theCurrentEntryCountsEvenWhenTheModelMarksItIrrelevant() {
        var history = List.of(job("10/2021", "Present", false), job("01/2010", "12/2019", false));

        var experience = calculator.calculate(history, "10/2021", "present", text(history));

        // 10/2021 to 10/2026 inclusive is 61 months.
        assertThat(experience.years()).hasValue(5);
        assertThat(experience.basis()).hasSize(1);
    }

    @Test
    void oneUnparseableRelevantDateMakesYearsUnknown() {
        assertThat(years(job("01/2020", "12/2022", true), job("Spring 2018", "12/2019", true)))
                .isEqualTo("unknown");
        assertThat(years(job("13/2020", "12/2022", true))).isEqualTo("unknown");
        assertThat(years(job("01/2020", "", true))).isEqualTo("unknown");
        assertThat(years(job("01/2020", null, true))).isEqualTo("unknown");
    }

    @Test
    void aRelevantDateThatIsNotInTheSentTextMakesYearsUnknown() {
        var history = List.of(job("01/2020", "12/2022", true));

        assertThat(calculator.calculate(history, "", "", "Worked from 2020 to 2022").years()).isEmpty();
        assertThat(calculator.calculate(history, "", "", "01/2020 – 12/2022").years()).hasValue(3);
        assertThat(calculator.calculate(history, "", "", null).years()).isEmpty();
    }

    @Test
    void presentStyleEndWordsNeedNotAppearInTheSentText() {
        var history = List.of(job("10/2024", "Present", true));

        assertThat(calculator.calculate(history, "", "", "Since 10/2024").years()).hasValue(2);
    }

    @Test
    void endBeforeStartMakesYearsUnknown() {
        assertThat(years(job("06/2022", "01/2022", true))).isEqualTo("unknown");
    }

    @Test
    void startAfterTheCurrentMonthMakesYearsUnknown() {
        assertThat(years(job("11/2026", "Present", true))).isEqualTo("unknown");
        assertThat(years(job("10/2026", "Present", true))).isEqualTo("0");
    }

    @Test
    void futureEndDatesOnlyCountTimeAlreadyServed() {
        // 01/2025 to 10/2026 is 22 months; counting to 12/2027 would give 36.
        assertThat(years(job("01/2025", "12/2027", true))).isEqualTo("2");
    }

    @Test
    void roundsToTheNearestYearWithSixMonthsRoundingUp() {
        assertThat(years(job("01/2020", "05/2021", true))).isEqualTo("1"); // 17 months: 1 year 5 months
        assertThat(years(job("01/2020", "06/2021", true))).isEqualTo("2"); // 18 months: 1 year 6 months
    }

    @Test
    void moreThanSeventyYearsMakesYearsUnknown() {
        assertThat(years(job("01/1957", "Present", true))).isEqualTo("70"); // 69 years 10 months
        assertThat(years(job("05/1956", "Present", true))).isEqualTo("unknown"); // 70 years 6 months
    }

    @Test
    void noRelevantEntriesOrNoHistoryMakesYearsUnknown() {
        assertThat(years(job("01/2010", "12/2019", false))).isEqualTo("unknown");
        assertThat(years()).isEqualTo("unknown");
        assertThat(calculator.calculate(null, "", "", "text").years()).isEmpty();
        assertThat(calculator.calculate(Arrays.asList(null, job("01/2024", "12/2024", true)), "", "",
                "01/2024 12/2024").years()).hasValue(1);
    }

    @ParameterizedTest
    @CsvSource({"09/2025, 1", "Oct 2025, 1", "2025, 2", "03/2021, 6", "October 2024, 2"})
    void aCurrentRoleStartedAtLeastTwelveMonthsAgoNeverGivesZero(String start, int expected) {
        var experience = calculator.calculate(List.of(job(start, "Present", true)), start, "Present",
                start + " - Present");

        assertThat(experience.years()).hasValue(expected);
        assertThat(experience.years().getAsInt()).isPositive();
    }

    @Test
    void basisIsEmptyWhenYearsAreUnknownAndHoldsAtMostTenJobs() {
        assertThat(calculator.calculate(List.of(job("Spring 2020", "Present", true)), "", "", "Spring 2020")
                .basis()).isEmpty();

        var history = new ArrayList<ProfessionalEmploymentEntry>();
        for (int year = 2000; year < 2012; year++) {
            history.add(new ProfessionalEmploymentEntry("Role " + year, "Employer " + year,
                    String.valueOf(year), String.valueOf(year), true));
        }
        var experience = calculator.calculate(history, "", "", text(history));
        assertThat(experience.years()).hasValue(12);
        assertThat(experience.basis()).hasSize(10).first().isEqualTo("Role 2000 at Employer 2000 (2000 – 2000)");
    }

    private static ProfessionalEmploymentEntry job(String start, String end, boolean relevant) {
        return new ProfessionalEmploymentEntry("Engineer", "Company", start, end, relevant);
    }

    /** Years for jobs whose dates all appear in the sent text, or "unknown". */
    private String years(ProfessionalEmploymentEntry... jobs) {
        var history = List.of(jobs);
        var years = calculator.calculate(history, "", "", text(history)).years();
        return years.isPresent() ? String.valueOf(years.getAsInt()) : "unknown";
    }

    private static String text(List<ProfessionalEmploymentEntry> history) {
        return history.stream().map(job -> job.start() + " - " + job.end()).collect(Collectors.joining("\n"));
    }
}
