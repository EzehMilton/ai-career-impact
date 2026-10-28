package com.chikere.futureai.coursemodule.university.analysis;

import com.chikere.futureai.coursemodule.university.application.UniversityModuleAnalysisRequest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Which of an assessment's modules are analysed. A module's id is its 1-based position in the course's stored module
 * list, which never changes once the assessment is saved.
 *
 * <p>With at most eight modules, every module is analysed in course order and any supplied choice is ignored: all of
 * them fit in one call, and analysing a chosen subset would let selective choice distort the course analysis. With
 * more than eight, the caller must pass an explicit choice of 1 to 8 ids; nothing is preselected and no "first
 * eight" rule exists. That choice is validated and then put back into course order, whatever order it was given in.</p>
 */
public final class UniversityModuleSelection {

    /** One selected module: its id (course position) and title. */
    public record SelectedModule(int moduleId, String title) {
        public SelectedModule {
            if (moduleId < 1) {
                throw new IllegalArgumentException("moduleId must be at least 1");
            }
            Objects.requireNonNull(title, "title must not be null");
        }
    }

    private UniversityModuleSelection() {
    }

    public static List<SelectedModule> resolve(List<String> modules, Optional<List<Integer>> selection) {
        Objects.requireNonNull(modules, "modules must not be null");
        Objects.requireNonNull(selection, "selection must not be null");
        if (modules.isEmpty()) {
            throw unavailable(UniversityModuleAnalysisUnavailableException.Reason.NO_MODULES,
                    "the assessment has no modules to analyse");
        }
        if (modules.stream().map(String::strip).distinct().count() != modules.size()) {
            throw unavailable(UniversityModuleAnalysisUnavailableException.Reason.DUPLICATE_MODULES,
                    "the assessment lists the same module more than once");
        }
        if (modules.size() <= UniversityModuleAnalysisRequest.MAXIMUM_MODULES) {
            List<SelectedModule> all = new ArrayList<>();
            for (int index = 0; index < modules.size(); index++) {
                all.add(new SelectedModule(index + 1, modules.get(index)));
            }
            return List.copyOf(all);
        }
        if (selection.isEmpty()) {
            throw unavailable(UniversityModuleAnalysisUnavailableException.Reason.MODULE_SELECTION_REQUIRED,
                    "the assessment has " + modules.size() + " modules; choose 1 to "
                            + UniversityModuleAnalysisRequest.MAXIMUM_MODULES + " to analyse");
        }
        List<Integer> chosen = selection.get();
        if (chosen.isEmpty() || chosen.size() > UniversityModuleAnalysisRequest.MAXIMUM_MODULES) {
            throw invalid("choose between 1 and " + UniversityModuleAnalysisRequest.MAXIMUM_MODULES + " modules");
        }
        Set<Integer> seen = new HashSet<>();
        for (Integer id : chosen) {
            if (id == null || id < 1 || id > modules.size()) {
                throw invalid("module id " + id + " does not exist in this assessment");
            }
            if (!seen.add(id)) {
                throw invalid("module id " + id + " was chosen more than once");
            }
        }
        return chosen.stream().sorted().map(id -> new SelectedModule(id, modules.get(id - 1))).toList();
    }

    private static UniversityModuleAnalysisUnavailableException invalid(String message) {
        return unavailable(UniversityModuleAnalysisUnavailableException.Reason.INVALID_SELECTION, message);
    }

    private static UniversityModuleAnalysisUnavailableException unavailable(
            UniversityModuleAnalysisUnavailableException.Reason reason, String message) {
        return new UniversityModuleAnalysisUnavailableException(reason, message);
    }
}
