package com.chikere.futureai.coursemodule.university.analysis;

import com.chikere.futureai.coursemodule.university.analysis.UniversityModuleAnalysisUnavailableException.Reason;
import com.chikere.futureai.coursemodule.university.analysis.UniversityModuleSelection.SelectedModule;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UniversityModuleSelectionTest {

    private static final List<String> EIGHT = modules(8);
    private static final List<String> TEN = modules(10);

    @Test
    void upToEightModulesAreAllAnalysedInCourseOrder() {
        assertThat(UniversityModuleSelection.resolve(EIGHT, Optional.empty())).extracting(SelectedModule::moduleId)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
        assertThat(UniversityModuleSelection.resolve(modules(3), Optional.empty())).containsExactly(
                new SelectedModule(1, "Module 1"), new SelectedModule(2, "Module 2"), new SelectedModule(3, "Module 3"));
        assertThat(UniversityModuleSelection.resolve(List.of("Only module"), Optional.empty()))
                .containsExactly(new SelectedModule(1, "Only module"));
    }

    @Test
    void withEightOrFewerModulesAnySuppliedSelectionIsIgnoredAndEveryModuleIsAnalysed() {
        // A valid subset cannot narrow the analysis.
        assertThat(UniversityModuleSelection.resolve(EIGHT, Optional.of(List.of(2)))).extracting(SelectedModule::moduleId)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
        assertThat(UniversityModuleSelection.resolve(modules(5), Optional.of(List.of(5, 1)))).extracting(
                SelectedModule::moduleId).containsExactly(1, 2, 3, 4, 5);
        // An otherwise invalid selection is ignored rather than rejected.
        for (List<Integer> invalid : List.of(List.<Integer>of(), List.of(99), List.of(2, 2), List.of(0),
                List.of(1, 2, 3, 4, 5, 6, 7, 8, 9))) {
            assertThat(UniversityModuleSelection.resolve(EIGHT, Optional.of(invalid))).as(invalid.toString())
                    .extracting(SelectedModule::moduleId).containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
        }
        assertThat(UniversityModuleSelection.resolve(EIGHT, Optional.of(Arrays.asList(1, null)))).hasSize(8);
    }

    @Test
    void moreThanEightRequiresAnExplicitSelectionAndNothingIsPreselected() {
        assertThatThrownBy(() -> UniversityModuleSelection.resolve(TEN, Optional.empty())).isInstanceOfSatisfying(
                UniversityModuleAnalysisUnavailableException.class,
                failure -> assertThat(failure.reason()).isEqualTo(Reason.MODULE_SELECTION_REQUIRED));
    }

    @Test
    void anExplicitSelectionIsPutBackIntoCourseOrderWhateverOrderItWasGivenIn() {
        assertThat(UniversityModuleSelection.resolve(TEN, Optional.of(List.of(10, 3, 7)))).containsExactly(
                new SelectedModule(3, "Module 3"), new SelectedModule(7, "Module 7"), new SelectedModule(10, "Module 10"));
        assertThat(UniversityModuleSelection.resolve(TEN, Optional.of(List.of(8, 7, 6, 5, 4, 3, 2, 1))))
                .extracting(SelectedModule::moduleId).containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
        assertThat(UniversityModuleSelection.resolve(modules(9), Optional.of(List.of(9)))).containsExactly(
                new SelectedModule(9, "Module 9"));
    }

    @Test
    void emptyTooLargeUnknownAndRepeatedSelectionsAreRefused() {
        for (List<Integer> invalid : List.of(List.<Integer>of(), List.of(1, 2, 3, 4, 5, 6, 7, 8, 9), List.of(0),
                List.of(11), List.of(-1), List.of(3, 3), List.of(1, 2, 1))) {
            assertThatThrownBy(() -> UniversityModuleSelection.resolve(TEN, Optional.of(invalid))).as(invalid.toString())
                    .isInstanceOfSatisfying(UniversityModuleAnalysisUnavailableException.class,
                            failure -> assertThat(failure.reason()).isEqualTo(Reason.INVALID_SELECTION));
        }
        assertThatThrownBy(() -> UniversityModuleSelection.resolve(TEN, Optional.of(Arrays.asList(1, null))))
                .isInstanceOf(UniversityModuleAnalysisUnavailableException.class);
    }

    @Test
    void anAssessmentWithoutModulesOrWithRepeatedModulesCannotBeAnalysed() {
        assertThatThrownBy(() -> UniversityModuleSelection.resolve(List.of(), Optional.empty())).isInstanceOfSatisfying(
                UniversityModuleAnalysisUnavailableException.class,
                failure -> assertThat(failure.reason()).isEqualTo(Reason.NO_MODULES));
        assertThatThrownBy(() -> UniversityModuleSelection.resolve(List.of("Typography", "Typography"), Optional.empty()))
                .isInstanceOfSatisfying(UniversityModuleAnalysisUnavailableException.class,
                        failure -> assertThat(failure.reason()).isEqualTo(Reason.DUPLICATE_MODULES));
    }

    private static List<String> modules(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(index -> "Module " + index).toList();
    }
}
