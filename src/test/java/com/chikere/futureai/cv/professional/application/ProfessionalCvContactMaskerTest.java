package com.chikere.futureai.cv.professional.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class ProfessionalCvContactMaskerTest {

    @Test
    void masksTheContactLineOfARealCvHeader() {
        assertThat(ProfessionalCvContactMasker.mask("LinkedIn | +44 7542 993 142 | cuttsbrians@gmail.com"))
                .isEqualTo("LinkedIn | [phone] | [email]");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Mobile: 07542 993142                      | Mobile: [phone]",
            "Office (01225) 123456 ext                 | Office [phone] ext",
            "Call +44 (0)7542 993142 after 5pm         | Call [phone] after 5pm",
            "Tel 07542-993-142.                        | Tel [phone].",
            "+447542993142                             | [phone]",
            "0117.496.0123                             | [phone]"})
    void masksUkPhoneNumbersInTheirCommonForms(String text, String expected) {
        assertThat(ProfessionalCvContactMasker.mask(text)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Email brian.cutts+cv@example.co.uk now    | Email [email] now",
            "Portfolio https://brian.dev/projects?x=1. | Portfolio [link].",
            "Site www.briancutts.co.uk                 | Site [link]",
            "linkedin.com/in/brian-cutts               | [link]",
            "uk.linkedin.com/in/brian-cutts            | [link]",
            "Code: github.com/bcutts/ai-career         | Code: [link]",
            "bcutts@github.com                         | [email]"})
    void masksEmailAddressesAndWebLinks(String text, String expected) {
        assertThat(ProfessionalCvContactMasker.mask(text)).isEqualTo(expected);
    }

    @Test
    void masksAFullUkPostcodeOnItsOwnAndInsideAnAddressLine() {
        assertThat(ProfessionalCvContactMasker.mask("BS1 4DJ")).isEqualTo("[postcode]");
        assertThat(ProfessionalCvContactMasker.mask("12 Queen Square, Bath BA1 2HN, United Kingdom"))
                .isEqualTo("12 Queen Square, Bath [postcode], United Kingdom");
        assertThat(ProfessionalCvContactMasker.mask("London SW1A 1AA")).isEqualTo("London [postcode]");
    }

    @Test
    void namesAndStreetAddressesAreLeftAlone() {
        String text = "Brian Cutts, 12 Queen Square, Bath";
        assertThat(ProfessionalCvContactMasker.mask(text)).isEqualTo(text);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "08/2025 - Present", "04/2023 – 08/2025", "03.2009 - 02.2016", "2019 - 2021", "March 2019 – Present",
            "03/2020", "3/2020", "03.2020", "03-2020", "March 2020", "Mar 2020", "mar. 2020", "Sept 2020", "2020",
            "03-2009-02-2016", "03.2009-02.2016", "2019-2021", "01/2018 to 02/2021", "2009 — 2016",
            "Software Engineer, Push Gaming, 08/2025 – Present",
            "EOD Technician, US Navy, 03/2009 – 02/2016"})
    void employmentDatesAndRangesAreNeverMasked(String text) {
        assertThat(ProfessionalCvContactMasker.mask(text)).isEqualTo(text);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Reduced costs by 20%", "Led deployments for over 150 operator brands", "Daily stand-ups of 1-2 hours",
            "Handled £1,250,000 of transactions in 2023", "Version 3.2.1 released to 40 clients"})
    void numbersInDutiesAreUntouched(String text) {
        assertThat(ProfessionalCvContactMasker.mask(text)).isEqualTo(text);
    }

    @Test
    void maskingALongUnbrokenRunOfCharactersStaysFast() {
        // The parser allows up to 100,000 characters; none of these shapes may make masking quadratic.
        for (String run : new String[] {"a".repeat(100_000), "1".repeat(100_000), "a.".repeat(50_000),
                "1 ".repeat(50_000) + "x", "www.".repeat(25_000), "a@".repeat(50_000)}) {
            assertTimeoutPreemptively(Duration.ofSeconds(2), () -> ProfessionalCvContactMasker.mask(run));
        }
    }

    @Test
    void textWithoutContactDetailsIsReturnedUnchanged() {
        String cv = """
                Software Engineer, Company A, 03/2021 – Present
                • Review pull requests for the “payments” team every day
                • Run the weekly on-call handover
                """;
        assertThat(ProfessionalCvContactMasker.mask(cv)).isEqualTo(cv);
        assertThat(ProfessionalCvContactMasker.mask("")).isEmpty();
        assertThat(ProfessionalCvContactMasker.mask(null)).isNull();
    }
}
