package com.chikere.futureai.cv.professional.fixture;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LocalFixtureProfessionalProfileExtractorTest {
    @Test
    void seniorArchitectFixtureIsDeterministicAndOffline() {
        var extractor = new LocalFixtureProfessionalProfileExtractor("senior_software_architect");
        var profile = extractor.extract("arbitrary local document text");
        assertThat(profile.currentRole()).isEqualTo("Senior Software Architect");
        assertThat(profile.currentEmployer()).isEqualTo("Northbridge Digital");
        assertThat(profile.currentRoleStart()).isEqualTo("March 2019");
        assertThat(profile.currentRoleEnd()).isEqualTo("Present");
    }

    @Test
    void fixtureYearsAreCalculatedFromItsOwnDatedHistory() {
        var clock = Clock.fixed(Instant.parse("2026-10-15T00:00:00Z"), ZoneOffset.UTC);

        var architect = new LocalFixtureProfessionalProfileExtractor("senior_software_architect", clock).extract("");
        // 09/2012 to 10/2026, the two jobs touching, is 14 years 2 months.
        assertThat(architect.yearsExperience()).hasValue(14);
        assertThat(architect.experienceBasis()).containsExactly(
                "Senior Software Architect at Northbridge Digital (March 2019 – Present)",
                "Software Engineer at Kestrel Systems (September 2012 – February 2019)");

        var nurse = new LocalFixtureProfessionalProfileExtractor("registered_nurse", clock).extract("");
        // The unrelated retail job is not counted: 09/2017 to 10/2026 is 9 years 2 months.
        assertThat(nurse.yearsExperience()).hasValue(9);
        assertThat(nurse.experienceBasis()).containsExactly(
                "Registered Nurse at St Aldric's Hospital (September 2017 – Present)");

        var developer = new LocalFixtureProfessionalProfileExtractor("routine_software_developer", clock).extract("");
        assertThat(developer.experienceBasis()).isEmpty();
    }

    @Test
    void fixtureYearsFollowTheInjectedClockNotTheSystemClock() {
        var thisYear = Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC);
        var nextYear = Clock.fixed(Instant.parse("2027-10-15T12:00:00Z"), ZoneOffset.UTC);

        for (var fixture : new String[] {"senior_software_architect", "electrician", "registered_nurse"}) {
            var now = new LocalFixtureProfessionalProfileExtractor(fixture, thisYear).extract("");
            var later = new LocalFixtureProfessionalProfileExtractor(fixture, nextYear).extract("");
            assertThat(later.yearsExperience().getAsInt()).as(fixture)
                    .isEqualTo(now.yearsExperience().getAsInt() + 1);
        }
        assertThat(new LocalFixtureProfessionalProfileExtractor("electrician", thisYear).extract("")
                .yearsExperience()).hasValue(13);
        assertThat(new LocalFixtureProfessionalProfileExtractor("electrician", nextYear).extract("")
                .yearsExperience()).hasValue(14);
    }

    @Test
    void reportsTheFixtureExtractorVersion() {
        assertThat(new LocalFixtureProfessionalProfileExtractor("electrician").version())
                .isEqualTo("professional-cv-extractor-fixture-v1");
    }

    @Test
    void routineDeveloperFixtureDoesNotInventYears() {
        var extractor = new LocalFixtureProfessionalProfileExtractor("routine_software_developer");
        var profile = extractor.extract("arbitrary local document text");
        assertThat(profile.currentRole()).isEqualTo("Software Developer");
        assertThat(profile.yearsExperience()).isEmpty();
    }
}
