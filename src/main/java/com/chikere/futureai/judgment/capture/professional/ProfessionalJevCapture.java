package com.chikere.futureai.judgment.capture.professional;

import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.assessment.professional.application.AssessmentSource;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;
import com.chikere.futureai.judgment.domain.DimensionJudgment;
import com.chikere.futureai.judgment.typesafe.professional.ProfessionalJevStateMapper;
import com.chikere.futureai.judgment.typesafe.professional.ProfessionalJudgmentSchema;
import org.springaicommunity.typesafe.JsonContent;
import org.springaicommunity.typesafe.question.Noul;
import org.springaicommunity.typesafe.question.Question;
import org.springaicommunity.typesafe.question.Score;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Builds the content of one capture: the exact state map and question wording Jev is given, and the
 * answers that came back. Pure: it writes nothing itself.
 */
final class ProfessionalJevCapture {
    private final ProfessionalJevStateMapper stateMapper;
    private final ProfessionalJudgmentSchema schema;

    ProfessionalJevCapture(ProfessionalJevStateMapper stateMapper, ProfessionalJudgmentSchema schema) {
        this.stateMapper = stateMapper;
        this.schema = schema;
    }

    Map<String, Object> document(AssessmentSource source, Instant capturedAt, ProfessionalAssessmentState state,
            AssessmentJudgmentSet judgments, Optional<String> modelIdentifier) {
        var document = new LinkedHashMap<String, Object>();
        // First, so a fixture capture cannot be mistaken for real Jev output.
        document.put("source", source.name());
        document.put("capturedAt", capturedAt.toString());
        document.put("schemaVersion", judgments.schemaVersion());
        document.put("modelIdentifier", modelIdentifier.orElse(null));
        document.put("state", stateMapper.map(state));
        document.put("questions", questions());
        document.put("answers", answers(judgments));
        return document;
    }

    private Map<String, Object> questions() {
        var questions = new LinkedHashMap<String, Object>();
        schema.questions().forEach((id, question) -> questions.put(id, question(question)));
        return questions;
    }

    private Map<String, Object> question(Question question) {
        var wording = new LinkedHashMap<String, Object>();
        wording.put("type", question.typeName());
        wording.put("instructions", text(question.instructions()));
        switch (question) {
            case Score score -> wording.put("levels", score.criteria().stream().map(this::text).toList());
            case Noul noul -> {
                wording.put("whenTrue", noul.criteria() == null ? null : text(noul.criteria().whenTrue()));
                wording.put("whenFalse", noul.criteria() == null ? null : text(noul.criteria().whenFalse()));
            }
            default -> {
            }
        }
        return wording;
    }

    private Map<String, Object> answers(AssessmentJudgmentSet judgments) {
        var answers = new LinkedHashMap<String, Object>();
        for (DimensionJudgment judgment : judgments.judgments()) {
            var answer = new LinkedHashMap<String, Object>();
            answer.put("value", judgment.numericValue().isPresent() ? judgment.numericValue().getAsDouble() : null);
            answer.put("nearestLabel", judgment.categoricalValue().orElse(null));
            answer.put("confidence",
                    judgment.confidenceValue().isPresent() ? judgment.confidenceValue().getAsDouble() : null);
            answer.put("probabilities", judgment.probabilityDistribution());
            answers.put(judgment.dimensionId(), answer);
        }
        return answers;
    }

    private String text(JsonContent content) {
        return content == null ? null : content.toDisplayString();
    }
}
