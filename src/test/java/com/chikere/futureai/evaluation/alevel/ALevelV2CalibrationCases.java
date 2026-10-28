package com.chikere.futureai.evaluation.alevel;

import com.chikere.futureai.assessment.domain.ALevelAssessmentState;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Stage A1: the seven fixed A-level calibration combinations. These are calibration inputs, not production special
 * cases: the decision policy never sees a subject name. Two reuse existing repository inputs; the rest are clearly
 * labelled calibration inputs. Several carry student context on purpose, to show it never reaches a v2 judgment.
 */
final class ALevelV2CalibrationCases {

    /**
     * @param v1FixtureId the {@code a-level-v1} local development fixture with the same subjects, if one exists, for
     *     the v1 baseline comparison
     */
    record CalibrationCase(String id, String source, ALevelAssessmentState state, Optional<String> v1FixtureId) {
    }

    private static final Optional<String> UK = Optional.of("United Kingdom");

    static final List<CalibrationCase> ALL = List.of(
            new CalibrationCase("maths-physics-computer-science", "A-level calibration input",
                    subjectsOnly("Mathematics", "Physics", "Computer Science"),
                    Optional.of("mathematics_physics_computer_science")),
            new CalibrationCase("maths-economics-geography", "Live-smoke case maths-economics-geography",
                    new ALevelAssessmentState(List.of("Mathematics", "Economics", "Geography"),
                            Map.of("Mathematics", "A", "Economics", "B", "Geography", "A"),
                            List.of("Solving maths problems", "Understanding how businesses and economies work",
                                    "Working with data and graphs", "Researching environmental and global issues",
                                    "Explaining ideas in written arguments"),
                            List.of("Economics", "Geography", "Business and Management", "Data-related courses"),
                            List.of("Finance", "Economic analysis", "Environmental consulting", "Business",
                                    "Technology"),
                            UK, Optional.of("I'm interested in keeping both business and environmental options "
                                    + "open. I'm also considering Computer Science, but I'm not yet sure whether I "
                                    + "want to take it instead of Geography.")),
                    Optional.of("maths_economics_geography")),
            new CalibrationCase("english-literature-history-politics", "Live-smoke case english-history-politics",
                    subjectsOnly("English Literature", "History", "Politics"),
                    Optional.of("english_history_politics")),
            new CalibrationCase("biology-chemistry-maths", "A-level calibration input",
                    new ALevelAssessmentState(List.of("Biology", "Chemistry", "Mathematics"),
                            Map.of("Biology", "A*", "Chemistry", "A"), List.of("Lab practicals"),
                            List.of("Medicine", "Biomedical science"), List.of("Healthcare"), UK, Optional.empty()),
                    Optional.of("biology_chemistry_mathematics")),
            new CalibrationCase("art-english-literature-psychology", "A-level calibration input",
                    new ALevelAssessmentState(List.of("Art and Design", "English Literature", "Psychology"),
                            Map.of(), List.of("Drawing", "Creative writing"), List.of("Illustration", "Psychology"),
                            List.of(), UK, Optional.empty()),
                    Optional.empty()),
            new CalibrationCase("business-economics-maths", "A-level calibration input",
                    subjectsOnly("Business Studies", "Economics", "Mathematics"), Optional.empty()),
            new CalibrationCase("sociology-psychology-biology", "A-level calibration input",
                    new ALevelAssessmentState(List.of("Sociology", "Psychology", "Biology"),
                            Map.of("Psychology", "B"), List.of("Understanding people"), List.of("Nursing", "Psychology"),
                            List.of("Social work"), UK, Optional.of("Undecided between healthcare and social work.")),
                    Optional.empty()));

    private ALevelV2CalibrationCases() {
    }

    private static ALevelAssessmentState subjectsOnly(String... subjects) {
        return new ALevelAssessmentState(List.of(subjects), Map.of(), List.of(), List.of(), List.of(),
                Optional.empty(), Optional.empty());
    }
}
