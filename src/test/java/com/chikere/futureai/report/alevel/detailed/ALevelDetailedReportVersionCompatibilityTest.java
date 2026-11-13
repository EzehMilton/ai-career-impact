package com.chikere.futureai.report.alevel.detailed;

import com.chikere.futureai.report.alevel.application.ALevelSubjectProfile;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ALevelDetailedReportVersionCompatibilityTest {

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @Test
    void v2JsonRoundTripsWithoutBeingRecomposedOrRelabelled() throws Exception {
        var historical = ALevelDetailedReportFixtures.report("maths_economics_geography");
        var storedJson = json.valueToTree(historical);

        var reloaded = json.treeToValue(storedJson, ALevelDetailedReport.class);

        com.fasterxml.jackson.databind.JsonNode reloadedJson = json.valueToTree(reloaded);
        assertThat(reloadedJson).isEqualTo(storedJson);
        assertThat(reloaded).isEqualTo(historical);
        assertThat(reloaded.reportVersion()).isEqualTo("a-level-report-v2");
        assertThat(reloaded.profile().optionsKeptOpen().name()).isEqualTo("Options Kept Open");
    }

    @Test
    void v3JsonRoundTripsWithTheSameInternalMeasuresAndNewLabels() throws Exception {
        var v2 = ALevelDetailedReportFixtures.report("maths_economics_geography");
        var v3 = ALevelDetailedReportFixtures.reportV3("maths_economics_geography");
        var reloaded = json.readValue(json.writeValueAsBytes(v3), ALevelDetailedReport.class);

        assertThat(reloaded).isEqualTo(v3);
        assertThat(reloaded.reportVersion()).isEqualTo("a-level-report-v3");
        assertThat(measures(v3.profile())).extracting(ALevelSubjectProfile.Measure::id)
                .containsExactlyElementsOf(measures(v2.profile()).stream().map(ALevelSubjectProfile.Measure::id).toList());
        assertThat(measures(v3.profile())).extracting(ALevelSubjectProfile.Measure::score,
                        ALevelSubjectProfile.Measure::band)
                .containsExactlyElementsOf(measures(v2.profile()).stream()
                        .map(measure -> org.assertj.core.groups.Tuple.tuple(measure.score(), measure.band())).toList());
        assertThat(v3.safeguards()).isEqualTo(v2.safeguards());
        assertThat(v3.limitations()).isEqualTo(v2.limitations());
        assertThat(v3.actionPlan()).isEqualTo(v2.actionPlan());
    }

    private static List<ALevelSubjectProfile.Measure> measures(ALevelSubjectProfile profile) {
        return List.of(profile.optionsKeptOpen(), profile.capabilityProfile().analyticalAndQuantitative(),
                profile.capabilityProfile().communicationInterpretationAndContext(), profile.learningAlongsideAi());
    }
}
