package com.chikere.futureai.evaluation.professional;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfessionalEvaluationDatasetTest {

    @Test
    void loadsAllFictionalProfessionalV1CasesAndComparisonPairs() {
        List<ProfessionalEvaluationCase> cases = ProfessionalEvaluationDataset.load();

        assertThat(cases).hasSize(16);
        assertThat(cases).extracting(ProfessionalEvaluationCase::id).contains(
                "data_entry_clerk",
                "routine_software_developer",
                "senior_software_architect",
                "transactional_accountant",
                "advisory_management_accountant",
                "graphic_production_designer",
                "creative_director",
                "insufficient_information");
        assertThat(ProfessionalEvaluationDataset.COMPARISON_PAIRS).hasSize(3);

        ProfessionalEvaluationCase insufficient = cases.stream()
                .filter(evaluationCase -> evaluationCase.id().equals("insufficient_information"))
                .findFirst()
                .orElseThrow();
        assertThat(insufficient.responsibilities()).isEmpty();
        assertThat(insufficient.skills()).isEmpty();
        assertThat(insufficient.yearsExperience()).isNull();
    }

    @Test
    void selectsOneCaseOrFailsClearlyForUnknownId() {
        List<ProfessionalEvaluationCase> cases = ProfessionalEvaluationDataset.load();

        assertThat(ProfessionalEvaluationDataset.select(cases, "routine_software_developer"))
                .singleElement()
                .extracting(ProfessionalEvaluationCase::id)
                .isEqualTo("routine_software_developer");
        assertThatThrownBy(() -> ProfessionalEvaluationDataset.select(cases, "not_a_case"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown jev.eval.case", "routine_software_developer");
    }
}
