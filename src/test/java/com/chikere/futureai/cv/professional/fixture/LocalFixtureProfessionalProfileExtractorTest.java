package com.chikere.futureai.cv.professional.fixture;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocalFixtureProfessionalProfileExtractorTest {
    @Test
    void seniorArchitectFixtureIsDeterministicAndOffline() {
        var extractor = new LocalFixtureProfessionalProfileExtractor("senior_software_architect");
        assertThat(extractor.extract("arbitrary local document text").currentRole())
                .isEqualTo("Senior Software Architect");
    }

    @Test
    void routineDeveloperFixtureDoesNotInventYears() {
        var extractor = new LocalFixtureProfessionalProfileExtractor("routine_software_developer");
        var profile = extractor.extract("arbitrary local document text");
        assertThat(profile.currentRole()).isEqualTo("Software Developer");
        assertThat(profile.yearsExperience()).isEmpty();
    }
}
