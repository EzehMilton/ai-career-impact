package com.chikere.futureai.narrative.alevel.application;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ALevelNarrativeValidatorTest {

    private final ALevelNarrativeValidator validator = new ALevelNarrativeValidator();

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
