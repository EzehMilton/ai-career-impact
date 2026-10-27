package com.chikere.futureai.responsibility.professional.guidance;

import com.chikere.futureai.responsibility.professional.analysis.ProfessionalResponsibilityAnalysisSnapshot;
import com.chikere.futureai.responsibility.professional.analysis.ResponsibilityAnalysisSnapshots;
import com.chikere.futureai.responsibility.professional.application.ProfessionalResponsibilityAnalysisRequest;
import com.chikere.futureai.responsibility.professional.application.ProfessionalResponsibilityJudgmentResult;
import com.chikere.futureai.responsibility.professional.application.ResponsibilityJudgmentSet;
import com.chikere.futureai.responsibility.professional.application.ResponsibilityJudgmentSource;
import com.chikere.futureai.responsibility.professional.application.ResponsibilityScoredJudgment;
import com.chikere.futureai.responsibility.professional.policy.ProfessionalResponsibilityPolicyV1;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Builds real analysis snapshots for guidance tests: either from the captured seven-role live Jev probe, or from
 * HAND-BUILT judgment rows {assistance, context, interaction, physical, accountability} classified by the real
 * responsibility policy. Hand-built rows are synthetic and only exercise rules.
 */
public final class AnalysisSnapshots {

    public static final String SEVEN_ROLES = "/calibration/responsibility-probe-2026-10-27-seven-roles.json";
    private static final ProfessionalResponsibilityPolicyV1 POLICY = new ProfessionalResponsibilityPolicyV1();

    private AnalysisSnapshots() {
    }

    /** A captured live run, for example {@code solicitor-8}. */
    public static ProfessionalResponsibilityAnalysisSnapshot live(String run) {
        JsonNode node = load(SEVEN_ROLES).get(run);
        List<ResponsibilityJudgmentSet> sets = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        for (JsonNode set : node.get("responsibilities")) {
            texts.add(set.get("responsibilityText").asText());
            sets.add(new ResponsibilityJudgmentSet(set.get("responsibilityIndex").asInt(), set.get("responsibilityKey").asText(),
                    set.get("responsibilityText").asText(), set.get("describedClearly").asDouble(),
                    judgment(set.get("assistanceScope")), judgment(set.get("contextualJudgement")),
                    judgment(set.get("humanInteraction")), judgment(set.get("physicalPresence")),
                    judgment(set.get("outcomeAccountability"))));
        }
        return snapshot(texts, "Captured " + run, List.of(), new ProfessionalResponsibilityJudgmentResult(
                node.get("schemaVersion").asText(), ResponsibilityJudgmentSource.JEV, Optional.of(node.get("model").asText()),
                Optional.empty(), OptionalLong.empty(), OptionalLong.empty(), judgment(node.get("regulatedContext")), sets));
    }

    /** Hand-built rows; responsibility texts are "Responsibility 1" and so on. */
    public static ProfessionalResponsibilityAnalysisSnapshot handBuilt(double regulated, double[]... rows) {
        return handBuilt("Test role", List.of(), regulated, rows);
    }

    public static ProfessionalResponsibilityAnalysisSnapshot handBuilt(String profession, List<String> skills,
            double regulated, double[]... rows) {
        List<String> texts = new ArrayList<>();
        for (int index = 0; index < rows.length; index++) {
            texts.add("Responsibility " + (index + 1));
        }
        return handBuilt(profession, skills, texts, regulated, rows);
    }

    /** Hand-built judgments with chosen responsibility texts, to show the text never affects the guidance. */
    public static ProfessionalResponsibilityAnalysisSnapshot handBuilt(String profession, List<String> skills,
            List<String> texts, double regulated, double[]... rows) {
        List<ResponsibilityJudgmentSet> sets = new ArrayList<>();
        for (int index = 0; index < rows.length; index++) {
            double[] row = rows[index];
            String text = texts.get(index);
            sets.add(new ResponsibilityJudgmentSet(index + 1, "r" + (index + 1), text, 0.5, value(row[0]), value(row[1]),
                    value(row[2]), value(row[3]), value(row[4])));
        }
        return snapshot(texts, profession, skills, new ProfessionalResponsibilityJudgmentResult("professional-responsibility-v1",
                ResponsibilityJudgmentSource.SYNTHETIC_FIXTURE, Optional.empty(), Optional.empty(), OptionalLong.empty(),
                OptionalLong.empty(), value(regulated), sets));
    }

    public static double[] row(double assistance, double context, double interaction, double physical, double accountability) {
        return new double[] {assistance, context, interaction, physical, accountability};
    }

    private static ProfessionalResponsibilityAnalysisSnapshot snapshot(List<String> texts, String profession,
            List<String> skills, ProfessionalResponsibilityJudgmentResult result) {
        UUID assessment = UUID.nameUUIDFromBytes((profession + texts).getBytes());
        var request = new ProfessionalResponsibilityAnalysisRequest(assessment, profession, "Work described.", texts, skills);
        return ResponsibilityAnalysisSnapshots.create(UUID.nameUUIDFromBytes(("snapshot" + profession + texts).getBytes()),
                assessment, request, result, POLICY.classify(result), Instant.EPOCH, Instant.EPOCH);
    }

    private static ResponsibilityScoredJudgment value(double value) {
        return new ResponsibilityScoredJudgment(value, Map.of(), OptionalDouble.empty(), OptionalInt.empty(), Optional.empty());
    }

    private static ResponsibilityScoredJudgment judgment(JsonNode node) {
        Map<Integer, Double> distribution = new LinkedHashMap<>();
        node.get("distribution").fields().forEachRemaining(level ->
                distribution.put(Integer.parseInt(level.getKey()), level.getValue().asDouble()));
        return new ResponsibilityScoredJudgment(node.get("value").asDouble(), distribution,
                node.hasNonNull("providerConfidence") ? OptionalDouble.of(node.get("providerConfidence").asDouble())
                        : OptionalDouble.empty(), OptionalInt.empty(), Optional.empty());
    }

    private static JsonNode load(String resource) {
        try (InputStream in = AnalysisSnapshots.class.getResourceAsStream(resource)) {
            return new ObjectMapper().readTree(in);
        }
        catch (java.io.IOException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
