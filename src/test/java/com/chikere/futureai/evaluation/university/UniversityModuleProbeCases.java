package com.chikere.futureai.evaluation.university;

import com.chikere.futureai.coursemodule.university.application.UniversityModuleAnalysisRequest;
import com.chikere.futureai.evaluation.university.UniversityBaselineCases.BaselineCase;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stage 5C probe selection: up to eight modules from each frozen Stage 5B course, chosen by hand for a spread of
 * module types and recorded here. This is a probe convenience only; the product will ask the student to choose
 * when a course has more than eight usable modules.
 */
final class UniversityModuleProbeCases {

    record ProbeCase(BaselineCase baselineCase, List<String> selectedModules, String selectionNote) {

        UniversityModuleAnalysisRequest request() {
            var state = baselineCase.state();
            return new UniversityModuleAnalysisRequest(UUID.nameUUIDFromBytes(baselineCase.id().getBytes()),
                    state.courseTitle(), state.courseDescription(), selectedModules, state.skillsDeveloped());
        }

        List<String> omittedModules() {
            return baselineCase.state().keyModules().stream().filter(module -> !selectedModules.contains(module))
                    .toList();
        }
    }

    private static final Map<String, Selection> SELECTIONS = Map.of(
            "computer-science", new Selection(List.of("Programming Fundamentals", "Algorithms and Data Structures",
                    "Discrete Mathematics", "Databases", "Software Engineering Group Project", "Artificial Intelligence",
                    "Cybersecurity", "Final-Year Individual Project"),
                    "Programming, theory, maths, data, team project, AI, security and the individual project; omits "
                            + "overlapping systems modules."),
            "business-management", new Selection(List.of("Principles of Management", "Introduction to Marketing",
                    "Financial and Management Accounting", "Organisational Behaviour", "Human Resource Management",
                    "Business Analytics", "Strategic Management", "Dissertation or Consultancy Project"),
                    "Broad management, people, quantitative and strategy modules plus the dissertation; omits "
                            + "operations and entrepreneurship."),
            "nursing", new Selection(List.of("Clinical Skills and Simulation", "Anatomy and Physiology",
                    "Pharmacology and Medicines Management", "Communication and Compassionate Care",
                    "Evidence-Based Practice", "Professional, Legal and Ethical Practice",
                    "Leadership and Management of Care", "Clinical Placements"),
                    "Clinical, science, pharmacology, communication, evidence, professional standards, leadership and "
                            + "placements; omits the foundations and conditions modules."),
            "law", new Selection(List.of("Legal Method and Skills", "Contract Law", "Criminal Law",
                    "Constitutional and Administrative Law", "Equity and Trusts", "EU and International Law",
                    "Law Clinic", "Dissertation"),
                    "Method, core doctrinal subjects, the law clinic and the dissertation; omits tort and land law."),
            "graphic-design", new Selection(List.of("Typography", "Visual Identity and Branding", "Illustration",
                    "Digital and Interaction Design", "Motion Graphics", "Design History and Theory", "Live Briefs",
                    "Final Major Project"),
                    "Studio craft, digital and motion work, theory, client briefs and the major project; omits "
                            + "fundamentals and professional practice."),
            "mechanical-engineering", new Selection(List.of("Engineering Mathematics", "Thermodynamics",
                    "Materials Science", "Mechanical Design and CAD", "Control Systems", "Manufacturing Processes",
                    "Engineering Design-and-Build Project", "Individual Project"),
                    "Maths, engineering science, CAD, control, manufacturing, the design-build project and the "
                            + "individual project; omits statics and fluids."),
            "english-literature", new Selection(List.of("Approaches to Literature", "Shakespeare",
                    "Romantic and Victorian Literature", "Modernism", "Contemporary Fiction", "Literary Theory",
                    "Creative Writing", "Dissertation"),
                    "Period study, theory, creative writing and the dissertation; omits medieval and Renaissance."));

    static final List<ProbeCase> ALL = UniversityBaselineCases.ALL.stream().map(baselineCase -> {
        Selection selection = SELECTIONS.get(baselineCase.id());
        return new ProbeCase(baselineCase, selection.modules(), selection.note());
    }).toList();

    /** Every case when {@code selection} is blank, otherwise the one named case; an unknown id fails. */
    static List<ProbeCase> select(String selection) {
        if (selection == null || selection.isBlank()) return ALL;
        String id = selection.strip();
        return List.of(ALL.stream().filter(value -> value.baselineCase().id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown university.module.probe.case '" + id
                        + "'. Known cases: " + ALL.stream().map(value -> value.baselineCase().id()).toList())));
    }

    private record Selection(List<String> modules, String note) {
    }

    private UniversityModuleProbeCases() {
    }
}
