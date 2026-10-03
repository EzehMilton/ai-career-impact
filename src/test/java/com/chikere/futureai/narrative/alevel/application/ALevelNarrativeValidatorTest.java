package com.chikere.futureai.narrative.alevel.application;

import com.chikere.futureai.assessment.alevel.application.AssessmentSource;
import com.chikere.futureai.assessment.domain.AssessmentConfidence;
import com.chikere.futureai.assessment.domain.AssessmentStatus;
import com.chikere.futureai.decision.alevel.ALevelMeasure;
import com.chikere.futureai.decision.alevel.OptionalityBand;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ALevelNarrativeValidatorTest {

    private final ALevelNarrativeValidator validator = new ALevelNarrativeValidator();

    @Test
    void balancedHumanAndCreativeBandRejectsLiveStrongWording() {
        ALevelNarrative drifted = withHumanExplanation(
                "You also have a strong human and creative aspect to your skill set.");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validator.validate(drifted, balancedContext()))
                .withMessageContaining("BALANCED human and creative");
    }

    @Test
    void balancedHumanAndCreativeBandAcceptsAlignedExplanation() {
        ALevelNarrative aligned = withHumanExplanation(
                "The balanced human and creative measure reflects communication and interpretation.");
        assertThat(validator.validate(aligned, balancedContext())).isSameAs(aligned);
    }

    private ALevelNarrative withHumanExplanation(String explanation) {
        ALevelNarrative base = narrative("These subjects appear to preserve several directions.");
        return new ALevelNarrative(base.headline(), base.summary(), base.whatThisMeans(),
                base.futureOptionalityExplanation(), base.strengthsExplanation(),
                base.degreePathwayExplanation(), base.analyticalFoundationExplanation(), explanation,
                base.transferabilityExplanation(), base.adaptabilityExplanation(),
                base.developmentOpportunitiesExplanation(), base.practicalActions(),
                base.cautionOrLimitations());
    }

    private ALevelNarrativeContext balancedContext() {
        var measures = java.util.Arrays.stream(ALevelMeasure.values())
                .map(measure -> new ALevelNarrativeContext.MeasureContext(measure, 6.5,
                        OptionalityBand.BALANCED, 0.8)).toList();
        return new ALevelNarrativeContext(List.of("Mathematics", "Economics", "Geography"),
                Map.of(), List.of(), List.of(), List.of(), Optional.empty(), Optional.empty(),
                AssessmentStatus.READY_WITH_CAUTION, AssessmentConfidence.MODERATE,
                measures, List.of(), List.of(), AssessmentSource.JEV, "a-level-v1", "a-level-decision-v1");
    }

    @Test
    void acceptsCalmBritishEnglishThatMakesNoExternalClaims() {
        ALevelNarrative narrative = narrative(
                "These subjects currently preserve a broad set of directions.");

        assertThat(validator.validate(narrative)).isSameAs(narrative);
    }

    @Test
    void rejectsRepeatedScoresAndPercentages() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validator.validate(narrative("Optionality is 8.3 / 10.")))
                .withMessageContaining("numeric scores");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validator.validate(narrative("Around 92% of students go on to study.")))
                .withMessageContaining("numeric scores");
    }

    @Test
    void rejectsFabricatedSalaryFigures() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validator.validate(narrative("Graduates typically earn £32,000.")))
                .withMessageContaining("salary");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validator.validate(narrative("Starting pay is around 30000 pounds.")))
                .withMessageContaining("salary");
    }

    @Test
    void rejectsInventedUniversityEntryRequirements() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validator.validate(narrative("Medicine requires Chemistry.")))
                .withMessageContaining("entry requirements");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validator.validate(narrative(
                        "You will need A-level Mathematics for this.")))
                .withMessageContaining("entry requirements");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validator.validate(narrative("The entry requirements are demanding.")))
                .withMessageContaining("entry requirements");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validator.validate(narrative("Most universities require Physics.")))
                .withMessageContaining("entry requirements");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validator.validate(narrative("You would need AAA for this course.")))
                .withMessageContaining("entry requirements");
    }

    @Test
    void permitsTellingTheStudentToCheckRequirementsThemselves() {
        // The safe phrasing must survive: the guard blocks invented facts, not sensible advice.
        ALevelNarrative narrative = narrative(
                "If a course interests you, check its own published subject requirements.");

        assertThat(validator.validate(narrative)).isSameAs(narrative);
    }

    @Test
    void rejectsAdmissionPromisesSubjectVerdictsAndDropInstructions() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validator.validate(narrative("This combination is future-proof.")))
                .withMessageContaining("unsupported admission");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validator.validate(narrative("This guarantees you a place.")))
                .withMessageContaining("unsupported admission");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validator.validate(narrative("You should drop Geography.")))
                .withMessageContaining("unsupported admission");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validator.validate(narrative("These are the wrong subjects.")))
                .withMessageContaining("unsupported admission");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validator.validate(narrative("These choices will limit your future.")))
                .withMessageContaining("unsupported admission");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validator.validate(narrative("You won't get into a good university.")))
                .withMessageContaining("unsupported admission");
    }

    @Test
    void checksEveryProseSectionAndEveryAction() {
        ALevelNarrative inActions = new ALevelNarrative("Headline", "Summary", "Meaning", "Optionality",
                "Strengths", "Pathways", "Analytical", "Human", "Transferability", "Adaptability",
                "Development",
                List.of("A sensible step", "This combination is future-proof", "Another sensible step"),
                "Caution");

        assertThatIllegalArgumentException().isThrownBy(() -> validator.validate(inActions));
        assertThatIllegalArgumentException().isThrownBy(() -> validator.validate(null));
    }

    private ALevelNarrative narrative(String injected) {
        return new ALevelNarrative("A practical reading of your subjects", injected,
                "This assessment suggests how wide a range of next steps these subjects preserve.",
                "Future optionality reflects the balance of breadth and transferable capability.",
                "Your combination appears to build strong structured analysis.",
                "The breadth of degree routes these subjects keep open is balanced.",
                "Your analytical foundation covers structured reasoning and quantitative confidence.",
                "Your human and creative strength covers communication and interpretation.",
                "Skill transferability describes how widely these capabilities apply.",
                "Adaptability describes the capabilities your subjects build.",
                "One area you may want to research further is technical and digital depth.",
                List.of("Look at how each subject is assessed.",
                        "Build some basic coding and data literacy.",
                        "Talk to your careers adviser about the options."),
                "This is not official guidance and not a statement about where you will end up.");
    }
}
