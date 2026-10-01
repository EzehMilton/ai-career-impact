package com.chikere.futureai.narrative.university.application;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class UniversityNarrativeValidatorTest {

    private final UniversityNarrativeValidator validator = new UniversityNarrativeValidator();

    @Test
    void acceptsCalmBritishEnglishThatMakesNoExternalClaims() {
        UniversityNarrative narrative = narrative("This course appears to develop structured analysis.");

        assertThat(validator.validate(narrative)).isSameAs(narrative);
    }

    @Test
    void rejectsRepeatedScoresAndPercentages() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validator.validate(narrative("Future Readiness is 8.75 / 10.")))
                .withMessageContaining("numeric scores");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validator.validate(narrative("Around 92% of students find this useful.")))
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
    void rejectsEmploymentGuaranteesAndFutureProofLanguage() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validator.validate(narrative("This degree is future-proof.")))
                .withMessageContaining("unsupported employment");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validator.validate(narrative("The employment rate is strong here.")))
                .withMessageContaining("unsupported employment");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validator.validate(narrative("This guarantees you a job.")))
                .withMessageContaining("unsupported employment");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validator.validate(narrative("Honestly this is a bad degree.")))
                .withMessageContaining("unsupported employment");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validator.validate(narrative("You will struggle to find work.")))
                .withMessageContaining("unsupported employment");
    }

    @Test
    void checksEveryProseSectionAndEveryAction() {
        UniversityNarrative inActions = new UniversityNarrative("Headline", "Summary", "Meaning",
                "Readiness", "Strengths", "Development", "Complementarity", "Transferability", "Adaptability",
                List.of("A sensible step", "This course is future-proof", "Another sensible step"), "Caution");

        assertThatIllegalArgumentException().isThrownBy(() -> validator.validate(inActions));
        assertThatIllegalArgumentException().isThrownBy(() -> validator.validate(null));
    }

    private UniversityNarrative narrative(String injected) {
        return new UniversityNarrative("A practical reading of this course", injected,
                "This assessment suggests how the course develops capability.",
                "Future Readiness reflects the balance of capabilities described.",
                "This course appears to develop independent judgement.",
                "One area you may want to strengthen is applied practice.",
                "There appears to be scope to work alongside AI tools.",
                "The capabilities may apply beyond one field.",
                "This describes the pathways the course opens, not your circumstances.",
                List.of("Look at how the modules are assessed.",
                        "Build practical experience with relevant AI tools.",
                        "Talk to current students about the course."),
                "This is not official guidance and not a guarantee about employment.");
    }
}
