package com.chikere.futureai.guidance.alevel;

import com.chikere.futureai.assessment.domain.ALevelAssessmentState;
import com.chikere.futureai.assessment.domain.AssessmentStatus;
import com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV2;
import com.chikere.futureai.decision.alevel.ALevelProfileBand;
import com.chikere.futureai.decision.alevel.ALevelProfileDecisionResult;
import com.chikere.futureai.decision.alevel.ALevelProfileMeasure;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV2.AI_LEARNING_SUPPORT;
import static com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV2.ANALYTICAL_REASONING;
import static com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV2.CONTEXTUAL_SOCIAL_UNDERSTANDING;
import static com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV2.DISCIPLINE_FAMILY_COVERAGE;
import static com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV2.ORIGINAL_JUDGMENT_CREATIVITY;
import static com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV2.QUANTITATIVE_REASONING;
import static com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV2.SUBJECT_SKILL_DIVERSITY;
import static com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV2.TECHNICAL_DIGITAL_FOUNDATION;
import static com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV2.WRITTEN_VERBAL_COMMUNICATION;

/**
 * {@code a-level-guidance-policy-v1}: the deterministic guidance of an A-level paid report.
 *
 * <p>Inputs are the frozen {@code a-level-decision-v2} subject profile (its bands and the stored scores of its
 * dimensions) and the student's guidance-only context. The context only personalises wording and actions; it never
 * changes a score, band or factor, and nothing here can. Pure Java: no Jev, no model, no evidence, no subject names
 * and no subject-specific branch. It never states an entry requirement, a grade, an acceptance, an accreditation rule,
 * an admission likelihood or a career outcome.</p>
 */
public final class ALevelGuidancePolicyV1 {

    public static final String POLICY_VERSION = "a-level-guidance-policy-v1";
    public static final String SUPPORTED_DECISION_POLICY_VERSION = ALevelDecisionPolicyV2.POLICY_VERSION;

    /** A capability at or above this stored score (0–4) is a strength worth developing further. */
    static final double STRENGTH_FROM = 3.0;
    /** A capability at or below this stored score is lighter enough for deliberate supplementing to be useful. */
    static final double LIGHTER_AT_MOST = 1.5;
    static final int MAXIMUM_STRENGTHS = 4;
    static final int MAXIMUM_AREAS = 3;
    static final int MAXIMUM_SKILLS = 5;
    static final int MAXIMUM_PREFERRED_AREAS = 3;

    public static final String AI_CAUTION = "AI assistance does not replace developing your own understanding, and it "
            + "does not mean AI use is permitted in assessed work.";

    static final String PREDICTED_GRADES = "predicted_grades";
    static final String STRENGTHS_AND_INTERESTS = "strengths_and_interests";
    static final String PREFERRED_DEGREE_AREAS = "preferred_degree_areas";
    static final String PREFERRED_CAREER_AREAS = "preferred_career_areas";

    /** The six capability dimensions in schema order; only these become strengths or areas to strengthen. */
    static final List<String> CAPABILITIES = List.of(QUANTITATIVE_REASONING, ANALYTICAL_REASONING,
            WRITTEN_VERBAL_COMMUNICATION, ORIGINAL_JUDGMENT_CREATIVITY, CONTEXTUAL_SOCIAL_UNDERSTANDING,
            TECHNICAL_DIGITAL_FOUNDATION);

    public ALevelGuidance decide(ALevelProfileDecisionResult profile, ALevelAssessmentState state) {
        Objects.requireNonNull(profile, "profile must not be null");
        Objects.requireNonNull(state, "state must not be null");
        if (!SUPPORTED_DECISION_POLICY_VERSION.equals(profile.policyVersion())) {
            throw new IllegalArgumentException("guidance supports " + SUPPORTED_DECISION_POLICY_VERSION + " only");
        }
        if (profile.assessmentDecision().status() == AssessmentStatus.MORE_INFORMATION_REQUIRED) {
            throw new IllegalArgumentException("a subject profile that needs more information has no paid guidance");
        }
        Profile p = new Profile(profile);
        List<ALevelGuidance.Item> strengths = strengths(p);
        List<ALevelGuidance.Item> areas = areas(p);
        return new ALevelGuidance(POLICY_VERSION, shape(p), strengths, areas, options(p, state), aiStudy(p),
                skills(p, strengths, areas, state), plan(p, strengths, areas, state), safeguards(p, state));
    }

    // ---- what the combination gives you ----

    private static List<ALevelGuidance.Item> shape(Profile p) {
        List<ALevelGuidance.Item> shape = new ArrayList<>();
        shape.add(switch (p.band(ALevelProfileMeasure.OPTIONS_KEPT_OPEN)) {
            case HIGH -> item("SHAPE_BREADTH", "Breadth across academic families", "Your subjects span distinct "
                    + "academic families and build different kinds of capability, which keeps a broad range of "
                    + "further-study directions open.", ALevelProfileMeasure.OPTIONS_KEPT_OPEN.name());
            case MODERATE -> item("SHAPE_BALANCED", "Some breadth with a clearer focus", "Your subjects combine some "
                    + "breadth with a clearer direction, keeping a balanced range of further-study options open.",
                    ALevelProfileMeasure.OPTIONS_KEPT_OPEN.name());
            case LOW -> item("SHAPE_FOCUSED", "A focused academic direction", "Your subjects concentrate on closely "
                    + "related areas. That is a focused direction rather than a narrow one: it builds depth for "
                    + "related further study.", ALevelProfileMeasure.OPTIONS_KEPT_OPEN.name());
        });
        ALevelProfileBand analytical = p.band(ALevelProfileMeasure.ANALYTICAL_AND_QUANTITATIVE);
        ALevelProfileBand communication = p.band(ALevelProfileMeasure.COMMUNICATION_INTERPRETATION_AND_CONTEXT);
        String a = ALevelProfileMeasure.ANALYTICAL_AND_QUANTITATIVE.name();
        String c = ALevelProfileMeasure.COMMUNICATION_INTERPRETATION_AND_CONTEXT.name();
        if (analytical == ALevelProfileBand.HIGH && communication == ALevelProfileBand.HIGH) {
            shape.add(item("SHAPE_MIXED", "A mixed capability profile", "Your subjects build strong analytical and "
                    + "quantitative work and strong communication, interpretation and context together.", a, c));
        }
        else if (analytical.ordinal() > communication.ordinal()) {
            shape.add(item("SHAPE_ANALYTICAL", "An analytical emphasis", "Your profile leans towards analytical and "
                    + "quantitative work, with communication, interpretation and context as the lighter side.", a, c));
        }
        else if (communication.ordinal() > analytical.ordinal()) {
            shape.add(item("SHAPE_COMMUNICATION", "A communication and interpretation emphasis", "Your profile leans "
                    + "towards communication, interpretation and context, with analytical and quantitative work as the "
                    + "lighter side.", a, c));
        }
        else {
            shape.add(item("SHAPE_EVEN", "An even capability profile", "Your subjects give similar emphasis to "
                    + "analytical and quantitative work and to communication, interpretation and context.", a, c));
        }
        shape.add(switch (p.band(ALevelProfileMeasure.LEARNING_ALONGSIDE_AI)) {
            case HIGH -> item("SHAPE_AI_HIGH", "Plenty of room for AI-supported study", "Much of the learning in your "
                    + "subjects lends itself to AI-supported explanation, practice and feedback.",
                    ALevelProfileMeasure.LEARNING_ALONGSIDE_AI.name());
            case MODERATE -> item("SHAPE_AI_MODERATE", "Some room for AI-supported study", "Part of the learning in "
                    + "your subjects lends itself to AI-supported explanation, practice and feedback.",
                    ALevelProfileMeasure.LEARNING_ALONGSIDE_AI.name());
            case LOW -> item("SHAPE_AI_LOW", "Your own practice leads", "Less of the learning in your subjects lends "
                    + "itself to AI support, so your own practice and guidance from teachers matter even more.",
                    ALevelProfileMeasure.LEARNING_ALONGSIDE_AI.name());
        });
        return shape;
    }

    // ---- strengths and areas to strengthen ----

    private static List<ALevelGuidance.Item> strengths(Profile p) {
        List<ALevelGuidance.Item> strengths = new ArrayList<>();
        p.ranked(CAPABILITIES, true).stream().filter(id -> p.score(id) >= STRENGTH_FROM)
                .forEach(id -> strengths.add(item("STRENGTH_" + upper(id), capability(id).title(),
                        capability(id).strength(), id)));
        if (p.score(DISCIPLINE_FAMILY_COVERAGE) >= STRENGTH_FROM) {
            strengths.add(item("STRENGTH_BREADTH", "Breadth across academic families", "Studying across distinct "
                    + "academic families lets you connect ideas from different fields; keep looking for those links.",
                    DISCIPLINE_FAMILY_COVERAGE));
        }
        if (p.score(SUBJECT_SKILL_DIVERSITY) >= STRENGTH_FROM) {
            strengths.add(item("STRENGTH_VARIETY", "A varied mix of capabilities", "Your subjects ask for different "
                    + "kinds of thinking; switching between them well is a capability in its own right.",
                    SUBJECT_SKILL_DIVERSITY));
        }
        return strengths.stream().limit(MAXIMUM_STRENGTHS).toList();
    }

    /** Only clearly lighter capabilities, where deliberate practice outside the subjects is a useful action. */
    private static List<ALevelGuidance.Item> areas(Profile p) {
        return p.ranked(CAPABILITIES, false).stream().filter(id -> p.score(id) <= LIGHTER_AT_MOST)
                .limit(MAXIMUM_AREAS)
                .map(id -> item("AREA_" + upper(id), capability(id).title(), capability(id).area(), id))
                .toList();
    }

    // ---- options and application considerations ----

    private static ALevelGuidance.Options options(Profile p, ALevelAssessmentState state) {
        String oko = ALevelProfileMeasure.OPTIONS_KEPT_OPEN.name();
        List<String> canSay = new ArrayList<>();
        canSay.add(switch (p.band(ALevelProfileMeasure.OPTIONS_KEPT_OPEN)) {
            case HIGH -> "Your subjects span several distinct academic families and build varied capabilities.";
            case MODERATE -> "Your subjects mix some breadth across academic families with a clearer focus.";
            case LOW -> "Your subjects are relatively focused on closely related academic families.";
        });
        canSay.add("The capabilities your subjects build, shown in your capability profile, carry into many kinds "
                + "of further study.");
        List<String> cannotSay = List.of(
                "It cannot tell you whether a particular course will accept your subjects.",
                "It does not check entry requirements, required subjects or grades for any course.",
                "It does not predict offers, admission or employment.");
        List<ALevelGuidance.Item> checks = new ArrayList<>();
        for (String area : state.preferredDegreeAreas().stream().limit(MAXIMUM_PREFERRED_AREAS).toList()) {
            checks.add(item("CHECK_DEGREE_" + checks.size(), "Check entry requirements for " + area, "You are "
                    + "interested in " + area + ". Check the published entry requirements of individual courses, "
                    + "particularly any required subjects or grades, and compare how courses in " + area + " differ "
                    + "in what they cover.", PREFERRED_DEGREE_AREAS, oko));
        }
        if (!state.preferredCareerAreas().isEmpty()) {
            String careers = joined(state.preferredCareerAreas().stream().limit(MAXIMUM_PREFERRED_AREAS).toList());
            checks.add(item("CHECK_CAREERS", "Exploring " + careers, "You mentioned an interest in " + careers + ". "
                    + "This report describes your subjects, not a career: explore what further study, training and "
                    + "experience people in those areas usually go through.", PREFERRED_CAREER_AREAS));
        }
        Optional<String> gradesNote = state.predictedGrades().isEmpty() ? Optional.empty() : Optional.of("You "
                + "shared predicted grades. Each course sets its own subject and grade requirements, so compare both "
                + "against the courses you are considering, and talk your choices through with a teacher or adviser.");
        return new ALevelGuidance.Options(canSay, cannotSay, checks, gradesNote);
    }

    // ---- AI study ----

    private static ALevelGuidance.AiStudy aiStudy(Profile p) {
        String lai = ALevelProfileMeasure.LEARNING_ALONGSIDE_AI.name();
        List<ALevelGuidance.Item> uses = new ArrayList<>();
        uses.add(item("AI_EXPLAIN", "Explain difficult ideas", "Ask AI to explain a concept in another way, then "
                + "check that you can explain it yourself without it.", AI_LEARNING_SUPPORT));
        uses.add(item("AI_PRACTICE", "Practise and test yourself", "Ask for practice questions on a topic, answer "
                + "them on your own, and use the feedback to find gaps.", AI_LEARNING_SUPPORT));
        if (p.score(QUANTITATIVE_REASONING) >= 2.5) {
            uses.add(item("AI_CALCULATIONS", "Check practice calculations", "Work a problem yourself first, then use "
                    + "AI to compare methods step by step; never copy a worked answer.", QUANTITATIVE_REASONING));
        }
        if (p.score(TECHNICAL_DIGITAL_FOUNDATION) >= 2.5) {
            uses.add(item("AI_DEBUG", "Understand errors in practice code", "Ask AI why your practice code fails, "
                    + "then fix it yourself so the understanding is yours.", TECHNICAL_DIGITAL_FOUNDATION));
        }
        if (p.score(WRITTEN_VERBAL_COMMUNICATION) >= 2.5) {
            uses.add(item("AI_DRAFTS", "Get feedback on practice drafts", "Ask for comments on the structure of a "
                    + "practice essay, then do the rewriting yourself.", WRITTEN_VERBAL_COMMUNICATION));
        }
        if (p.score(ORIGINAL_JUDGMENT_CREATIVITY) >= 2.5 || p.score(CONTEXTUAL_SOCIAL_UNDERSTANDING) >= 2.5) {
            uses.add(item("AI_CHALLENGE", "Challenge your arguments", "Ask AI to argue against your view or suggest "
                    + "another interpretation, then decide for yourself which holds up.", ORIGINAL_JUDGMENT_CREATIVITY,
                    CONTEXTUAL_SOCIAL_UNDERSTANDING));
        }
        uses.add(item("AI_REVISION", "Organise your revision", "Use AI to turn your notes into a revision plan or "
                + "flashcards, and check them against your own notes.", AI_LEARNING_SUPPORT));
        int depth = switch (p.band(ALevelProfileMeasure.LEARNING_ALONGSIDE_AI)) {
            case LOW -> 3;
            case MODERATE -> 4;
            case HIGH -> 5;
        };
        List<ALevelGuidance.Item> chosen = new ArrayList<>(uses.stream().limit(depth).toList());
        chosen = chosen.stream().map(use -> withProvenance(use, lai)).toList();
        return new ALevelGuidance.AiStudy(AI_CAUTION, chosen);
    }

    // ---- skill priorities ----

    private static List<ALevelGuidance.Item> skills(Profile p, List<ALevelGuidance.Item> strengths,
            List<ALevelGuidance.Item> areas, ALevelAssessmentState state) {
        Map<String, ALevelGuidance.Item> skills = new LinkedHashMap<>();
        strengths.stream().filter(strength -> strength.id().startsWith("STRENGTH_") && !strength.id().equals(
                "STRENGTH_BREADTH") && !strength.id().equals("STRENGTH_VARIETY")).findFirst()
                .ifPresent(strength -> skills.put("DEEPEN", item("SKILL_DEEPEN", "Deepen "
                        + lowerFirst(strength.title()), "Your subjects already build this; stretch it further with "
                        + "harder problems, wider reading or an independent project.",
                        strength.provenance().toArray(String[]::new))));
        areas.stream().findFirst().ifPresent(area -> skills.put("SUPPLEMENT", item("SKILL_SUPPLEMENT",
                "Build " + lowerFirst(area.title()), "Your subjects place less emphasis on this, so deliberate "
                        + "practice outside them adds range.", area.provenance().toArray(String[]::new))));
        skills.put("INDEPENDENT", item("SKILL_INDEPENDENT", "Working without AI first", "Attempt work on your own "
                + "before asking AI, so that the understanding you are assessed on is your own.",
                AI_LEARNING_SUPPORT, ALevelProfileMeasure.LEARNING_ALONGSIDE_AI.name()));
        skills.put("VERIFY", item("SKILL_VERIFY", "Checking information", "Check what AI or any single source tells "
                + "you against your notes, textbooks and teachers.", AI_LEARNING_SUPPORT));
        if (!state.strengthsAndInterests().isEmpty()) {
            String interest = state.strengthsAndInterests().getFirst();
            skills.put("INTEREST", item("SKILL_INTEREST", "Build on your interest in " + lowerFirst(interest),
                    "You named this as a strength or interest; find ways to develop it alongside your subjects.",
                    STRENGTHS_AND_INTERESTS));
        }
        if (skills.size() < 3) {
            skills.put("BREADTH", item("SKILL_BREADTH", "Connecting ideas across subjects", "Look for links between "
                    + "what your subjects teach; explaining those links is good practice for further study.",
                    SUBJECT_SKILL_DIVERSITY));
        }
        return skills.values().stream().limit(MAXIMUM_SKILLS).toList();
    }

    // ---- action plan ----

    private static ALevelGuidance.ActionPlan plan(Profile p, List<ALevelGuidance.Item> strengths,
            List<ALevelGuidance.Item> areas, ALevelAssessmentState state) {
        String lai = ALevelProfileMeasure.LEARNING_ALONGSIDE_AI.name();
        String oko = ALevelProfileMeasure.OPTIONS_KEPT_OPEN.name();
        List<ALevelGuidance.Item> now = new ArrayList<>();
        now.add(item("NOW_AI_HABIT", "Set one AI study habit", "Choose one way you will use AI for revision this "
                + "week, and one topic you will practise entirely without it.", lai));
        strengths.stream().findFirst().ifPresent(strength -> now.add(item("NOW_KEEP_EXAMPLES",
                "Keep examples of your best work", "Start a folder of work that shows " + lowerFirst(strength.title())
                        + "; it will help when you write about yourself later.",
                strength.provenance().toArray(String[]::new))));
        if (state.preferredDegreeAreas().isEmpty()) {
            now.add(item("NOW_LIST_AREAS", "Note down areas you might study", "Write down two or three subject areas "
                    + "you might like to study after A-levels; it is fine for this list to change.", oko));
        }
        else {
            now.add(item("NOW_LIST_COURSES", "List courses to look into", "Note a few courses in "
                    + joined(state.preferredDegreeAreas().stream().limit(MAXIMUM_PREFERRED_AREAS).toList())
                    + " to research properly later.", PREFERRED_DEGREE_AREAS));
        }

        List<ALevelGuidance.Item> sixthForm = new ArrayList<>();
        if (!areas.isEmpty()) {
            ALevelGuidance.Item area = areas.getFirst();
            sixthForm.add(item("SF_BUILD_AREA", "Build " + lowerFirst(area.title()), "Take on a project, online course "
                    + "or activity that builds this alongside your subjects.",
                    area.provenance().toArray(String[]::new)));
        }
        strengths.stream().findFirst().ifPresent(strength -> sixthForm.add(item("SF_STRETCH",
                "Stretch " + lowerFirst(strength.title()), "Use wider reading, a competition or an independent "
                        + "project to take this further than the syllabus.",
                strength.provenance().toArray(String[]::new))));
        sixthForm.add(item("SF_AI_RULES", "Ask how AI may be used in each subject", "Ask each teacher what AI use is "
                + "allowed in coursework and non-exam assessment for their subject.", lai));
        sixthForm.add(item("SF_TALK", "Talk to a teacher or adviser", "Discuss how your subjects connect to the "
                + "further study you are considering.", oko));

        List<ALevelGuidance.Item> applications = new ArrayList<>();
        applications.add(item("APP_REQUIREMENTS", "Check each course's published requirements", "For every course "
                + "you consider, check the published entry requirements, including any required subjects and grades.",
                oko));
        if (!state.predictedGrades().isEmpty()) {
            applications.add(item("APP_GRADES", "Compare grades with requirements", "Compare your predicted grades "
                    + "with each course's published requirements, and discuss your options with an adviser.",
                    PREDICTED_GRADES));
        }
        if (p.band(ALevelProfileMeasure.OPTIONS_KEPT_OPEN) == ALevelProfileBand.LOW) {
            applications.add(item("APP_FOCUSED", "Compare related courses", "Your subjects are focused, so compare how "
                    + "related courses differ, and check which build on your subjects most directly.", oko));
        }
        applications.add(item("APP_ROUTES", "Look at more than one route", "Apprenticeships and other post-18 routes "
                + "publish their own requirements too; compare them alongside degrees.", oko));
        return new ALevelGuidance.ActionPlan(now, sixthForm, applications);
    }

    // ---- safeguards ----

    private static List<ALevelGuidance.Item> safeguards(Profile p, ALevelAssessmentState state) {
        String lai = ALevelProfileMeasure.LEARNING_ALONGSIDE_AI.name();
        String oko = ALevelProfileMeasure.OPTIONS_KEPT_OPEN.name();
        List<ALevelGuidance.Item> safeguards = new ArrayList<>();
        safeguards.add(item("SG1", "Entry requirements vary", "Courses set their own subject and grade requirements; "
                + "always check each course's published requirements.", oko));
        safeguards.add(item("SG2", "Assessment rules come first", "Schools, colleges and exam boards set their own "
                + "rules for AI in assessed work; check them before using AI on anything that is assessed.", lai));
        if (p.band(ALevelProfileMeasure.LEARNING_ALONGSIDE_AI) != ALevelProfileBand.LOW) {
            safeguards.add(item("SG3", "Develop the capability yourself", "The more AI can help with your subjects, "
                    + "the more important it is not to hand it the thinking you need to learn.", lai));
        }
        safeguards.add(item("SG4", "Check what AI tells you", "AI can be wrong or invent information, including "
                + "sources and facts; check anything important.", AI_LEARNING_SUPPORT));
        if (!state.predictedGrades().isEmpty() || !state.preferredDegreeAreas().isEmpty()) {
            safeguards.add(item("SG5", "This is not an admissions prediction", "This report does not predict offers "
                    + "or admission for any course.", state.predictedGrades().isEmpty() ? PREFERRED_DEGREE_AREAS
                    : PREDICTED_GRADES));
        }
        return safeguards;
    }

    // ---- helpers ----

    private record Capability(String title, String strength, String area) {
    }

    private static Capability capability(String id) {
        return switch (id) {
            case QUANTITATIVE_REASONING -> new Capability("Mathematical and statistical reasoning",
                    "Your subjects build mathematical and statistical reasoning; keep practising problems that make "
                            + "you choose the method yourself.",
                    "Your subjects place less emphasis on mathematical and statistical reasoning. Consider "
                            + "deliberately developing some confidence with numbers and data, for example through "
                            + "spreadsheets or a statistics course.");
            case ANALYTICAL_REASONING -> new Capability("Structured analysis and problem solving",
                    "Your subjects build structured analysis; practise breaking unfamiliar problems into steps.",
                    "Your subjects place less emphasis on structured analysis. You may want to build additional "
                            + "experience of breaking problems into steps, for example through puzzles or debating.");
            case WRITTEN_VERBAL_COMMUNICATION -> new Capability("Extended writing and argument",
                    "Your subjects build extended writing and argument; practise making a case concisely as well as "
                            + "at length.",
                    "Your subjects place less emphasis on extended writing. Consider deliberately developing it, for "
                            + "example through an extended project, a blog or a debating club.");
            case ORIGINAL_JUDGMENT_CREATIVITY -> new Capability("Interpretation, originality and design",
                    "Your subjects build interpretation and original work; keep a record of your own ideas and "
                            + "projects.",
                    "Your subjects place less emphasis on open-ended, original work. You may want to build "
                            + "additional experience through a creative or independent project.");
            case CONTEXTUAL_SOCIAL_UNDERSTANDING -> new Capability("Understanding human, social and historical context",
                    "Your subjects build an understanding of people and society; connect it to current events.",
                    "Your subjects place less emphasis on human and social context. Consider deliberately widening "
                            + "your reading about history, economics or society.");
            case TECHNICAL_DIGITAL_FOUNDATION -> new Capability("Technical and computational work",
                    "Your subjects build technical and computational skills; try applying them in a small project "
                            + "of your own.",
                    "Your subjects place less emphasis on technical and computational work. You may want to build "
                            + "additional experience, for example a short coding or data course.");
            default -> throw new IllegalArgumentException("not a capability: " + id);
        };
    }

    private static ALevelGuidance.Item item(String id, String title, String text, String... provenance) {
        return new ALevelGuidance.Item(id, title, text, List.of(provenance));
    }

    private static ALevelGuidance.Item withProvenance(ALevelGuidance.Item item, String extra) {
        Set<String> provenance = new LinkedHashSet<>(item.provenance());
        provenance.add(extra);
        return new ALevelGuidance.Item(item.id(), item.title(), item.text(), List.copyOf(provenance));
    }

    private static String upper(String id) {
        return id.toUpperCase(Locale.ROOT);
    }

    private static String lowerFirst(String value) {
        return value.isEmpty() ? value : Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }

    private static String joined(List<String> values) {
        if (values.size() <= 1) return String.join("", values);
        return String.join(", ", values.subList(0, values.size() - 1)) + " and " + values.getLast();
    }

    /** The frozen bands and the stored score of every dimension, read from the decision's own contributions. */
    private static final class Profile {
        private final ALevelProfileDecisionResult decision;
        private final Map<String, Double> scores = new LinkedHashMap<>();

        Profile(ALevelProfileDecisionResult decision) {
            this.decision = decision;
            decision.measures().values().forEach(measure -> measure.contributions()
                    .forEach(factor -> scores.putIfAbsent(factor.dimensionId(), factor.score())));
            if (!scores.keySet().containsAll(ALevelDecisionPolicyV2.SCORE_DIMENSIONS)) {
                throw new IllegalArgumentException("the subject profile is missing a stored dimension");
            }
        }

        ALevelProfileBand band(ALevelProfileMeasure measure) {
            return decision.measure(measure).orElseThrow().band();
        }

        double score(String dimensionId) {
            return scores.get(dimensionId);
        }

        /** Highest (or lowest) stored score first; schema order breaks ties, so the order is deterministic. */
        List<String> ranked(List<String> dimensions, boolean highestFirst) {
            Comparator<String> byScore = Comparator.comparingDouble(this::score);
            return dimensions.stream().sorted((highestFirst ? byScore.reversed() : byScore)
                    .thenComparingInt(ALevelDecisionPolicyV2.SCORE_DIMENSIONS::indexOf)).toList();
        }
    }
}
