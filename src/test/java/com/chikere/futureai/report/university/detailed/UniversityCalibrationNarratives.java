package com.chikere.futureai.report.university.detailed;

import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrative;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrative.ActionPlanNote;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrative.AiStudyStrategy;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrative.HumanCapabilityInsight;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrative.ModuleLearningInsight;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrative.SkillInsight;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrativeContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Hand-written university-paid-narrative-v2 drafts for the two calibration courses: the specificity the paid report
 * should reach, written as test data and held to every validator rule. They are quality calibrators, not production
 * content: nothing here is read by production code, and no course name is mapped to prose anywhere in main.
 *
 * <p>Each draft names the ids it expects, in the deterministic order; if the frozen guidance ever selects differently,
 * the fixture fails loudly instead of quietly mislabelling a module.</p>
 */
public final class UniversityCalibrationNarratives {

    private UniversityCalibrationNarratives() {
    }

    public static UniversityPaidNarrative computerScience(UniversityPaidNarrativeContext context) {
        expect(context.moduleLearningIds(), "module-1", "module-4", "module-3", "module-2");
        expect(context.humanCapabilityIds(), "module-6");
        expect(context.skillIds(), "skill-technical-foundations", "skill-ai-literacy", "skill-practical-application");
        List<String> notes = List.of(
                "Choose a function from a recent lab and mark each step of the AI explanation as correct, wrong or "
                        + "unclear before you rely on any of it.",
                "Pick the topic that took you longest on the last problem sheet and work one example fully by hand "
                        + "before reading any explanation of it.",
                "Ask for problems slightly beyond the lab exercises, and keep your own solutions so you can see your "
                        + "progress over the term.",
                "Agree with your team which practice, such as code review or estimating tasks, each of you will "
                        + "focus on, and ask a teammate to observe.",
                "Keep a short log for each problem: your method, the AI critique, and what you changed and why.",
                "Ask specifically about your design choices and how clearly you explained them, not only whether the "
                        + "feature worked.",
                "A small project combining a database with a simple model would draw on several of your modules; "
                        + "keep notes on every AI-assisted part you checked.",
                "Write a short account of how you use AI when studying, for example asking for critique before "
                        + "rewriting, that you could explain to a tutor.",
                "Include design notes, review comments and commit history from the group project to show how your "
                        + "contribution developed.",
                "Open-source contributions, hackathons or a student society project would add team experience with "
                        + "real users.");
        return new UniversityPaidNarrative(
                "Your course is built on modules where AI can be a genuinely useful study partner: it can explain an "
                        + "unfamiliar library, critique a schema or set fresh practice on a tricky algorithm. The same "
                        + "modules expect you to show that you can design, write, trace and test software yourself, so "
                        + "the value comes from using AI to question your work rather than to produce it.\n\n"
                        + "The sections below take each analysed module in turn: how AI could help, what you must still "
                        + "be able to do on your own, and one small experiment to try. The group project adds a "
                        + "different emphasis, where your own judgement and teamwork carry most weight.",
                "Read together, high Working Alongside AI and high Transferable Capability sit alongside moderate "
                        + "Human and Practical Capability: the course builds technical depth that AI tools can support, "
                        + "while hands-on team work still depends on you.",
                "Most of your analysed modules share a technical core, but they differ in what you are learning to do: "
                        + "write and debug programs, design and query data, reason about search and knowledge, or train "
                        + "and evaluate models. The group project is where practical teamwork and your own decisions "
                        + "lead.",
                List.of(
                        new ModuleLearningInsight("module-1",
                                "Programming is learned by writing, running and fixing your own programs, so AI helps "
                                        + "most as a reviewer and explainer once you have made an attempt, not as the "
                                        + "author of the solution.",
                                List.of("Explaining an unfamiliar language feature or library call with a small example "
                                                + "you then modify yourself",
                                        "Suggesting edge-case tests for a function you have written, which you run and "
                                                + "interpret",
                                        "Critiquing the structure and naming of working code so you can refactor it "
                                                + "yourself",
                                        "Comparing two ways of implementing the same small feature and the trade-off "
                                                + "between them"),
                                List.of("Tracing what your program does line by line, without running it",
                                        "Debugging a failing program yourself before asking for help",
                                        "Explaining why your solution works, in your own words"),
                                "Write a solution to one lab exercise on your own, then ask AI to review it for bugs and "
                                        + "missing test cases. Fix only what you can explain, and note which "
                                        + "suggestions you rejected and why."),
                        new ModuleLearningInsight("module-4",
                                "Machine learning mixes mathematics, data and judgement about results, so AI is most "
                                        + "useful for seeing an idea from several angles and for testing your "
                                        + "reasoning about why a model behaves as it does.",
                                List.of("Explaining gradient descent or regularisation in several different ways until "
                                                + "one makes sense",
                                        "Generating short questions on bias, variance and overfitting that you answer "
                                                + "before checking",
                                        "Critiquing your choice of evaluation metric for a given dataset and task",
                                        "Suggesting reasons a model might underperform, which you then test against the "
                                                + "data"),
                                List.of("Working through the core mathematics and assumptions of a model by hand",
                                        "Choosing and justifying evaluation metrics yourself",
                                        "Recognising overfitting or data leakage in your own results"),
                                "Train a small model yourself and write a paragraph interpreting its results. Then ask "
                                        + "AI to challenge your interpretation, and check each challenge against the "
                                        + "evidence before changing your conclusion."),
                        new ModuleLearningInsight("module-3",
                                "This module asks you to understand how search, reasoning and learning methods work, "
                                        + "so AI can help you rehearse the ideas while you learn to apply the "
                                        + "algorithms and judge their limits yourself.",
                                List.of("Walking through a search algorithm on a small example graph, step by step, after "
                                                + "you have tried it",
                                        "Contrasting knowledge representations or search strategies for the same "
                                                + "problem",
                                        "Setting exam-style questions on heuristics and admissibility that you answer "
                                                + "first",
                                        "Finding counter-examples where a method you studied gives a poor answer"),
                                List.of("Applying search and inference algorithms by hand on new problems",
                                        "Explaining the assumptions and limitations of a technique"),
                                "Solve one search or planning problem on paper, then ask AI for its solution and compare "
                                        + "the two step by step, noting exactly where and why they differ."),
                        new ModuleLearningInsight("module-2",
                                "Database work rewards precise reasoning about data, so AI is useful for practice "
                                        + "queries and design critique, as long as you check every generated query "
                                        + "against the requirements yourself.",
                                List.of("Generating SQL practice questions on joins and grouping against a schema you "
                                                + "know",
                                        "Explaining how an index or query plan changes the cost of a query",
                                        "Critiquing a schema you designed for redundancy and normalisation problems",
                                        "Setting normalisation exercises that you work through by hand"),
                                List.of("Writing and reading SQL without help",
                                        "Reasoning about schema design and its trade-offs",
                                        "Checking whether a generated query actually meets the requirement"),
                                "Design a small schema for a scenario from your coursework, then ask AI to critique it. "
                                        + "Accept only the changes you can justify, and write a query by hand to test "
                                        + "each one.")),
                List.of(new HumanCapabilityInsight("module-6",
                        "In a group project the work that counts is negotiated with other people: agreeing "
                                + "requirements, dividing tasks, reviewing each other's work and resolving "
                                + "disagreements. You build those skills only by doing them with your team.",
                        "Be able to explain and defend your team's design decisions, and your own contribution, in a "
                                + "review without notes.")),
                List.of(new SkillInsight("skill-technical-foundations",
                                "Your analysed modules share a technical core, and that foundation is what lets you "
                                        + "judge whether AI output is right.",
                                List.of("Understanding algorithms well enough not to accept generated code at face value",
                                        "Tracing program logic and query results by hand when something looks wrong",
                                        "Checking generated SQL or model evaluation against what the task requires")),
                        new SkillInsight("skill-ai-literacy",
                                "Because AI can support so much of the learning in this course, using it well becomes "
                                        + "a skill in itself.",
                                List.of("Asking AI to critique your code rather than write it",
                                        "Recording where an AI suggestion was wrong and how you spotted it")),
                        new SkillInsight("skill-practical-application",
                                "The group project is where you apply technical knowledge in a working team, under "
                                        + "real constraints.",
                                List.of("Turning requirements into a working, tested feature with your team",
                                        "Using code review and testing to keep shared work reliable"))),
                notes(context, notes),
                new AiStudyStrategy(
                        List.of("Explaining unfamiliar concepts, libraries and algorithms in different ways",
                                "Reviewing your code, schemas and test cases after you have written them",
                                "Generating fresh practice problems on the topics you find hardest"),
                        List.of("Writing, tracing and debugging programs from scratch",
                                "Working the mathematics behind machine learning by hand",
                                "Designing schemas and writing SQL without assistance"),
                        List.of("Run and test any generated code before trusting it",
                                "Check generated queries and model evaluations against the real requirements and data"),
                        List.of("Keep commits, drafts and design notes that show your work developing",
                                "Be ready to explain any line of code you submit"),
                        List.of("A portfolio of projects with your own design decisions explained",
                                "A log of problems you solved and mistakes you found in AI output")),
                "Studying computer science alongside AI works best when AI questions your work and you remain the "
                        + "person who can design, explain and fix it.");
    }

    public static UniversityPaidNarrative nursing(UniversityPaidNarrativeContext context) {
        expect(context.moduleLearningIds(), "module-2", "module-4", "module-6", "module-1");
        expect(context.humanCapabilityIds(), "module-7", "module-3", "module-5", "module-1");
        expect(context.skillIds(), "skill-professional-judgement", "skill-communication-and-collaboration",
                "skill-critical-evaluation", "skill-practical-application", "skill-technical-foundations");
        List<String> notes = List.of(
                "Ask your module lead or practice supervisor which guidance and textbooks they expect you to use, and "
                        + "keep them where you revise.",
                "Pick a reading on person-centred care, and check whether every reference the AI gives actually exists "
                        + "and supports the claim.",
                "Choose something specific, such as explaining a procedure to a patient, and agree with your "
                        + "supervisor how they will observe it.",
                "Body systems with many interacting parts, such as the renal system, are good candidates; draw the "
                        + "process yourself before reading any explanation.",
                "Choose a topic such as consent or confidentiality, and record where the AI answer was vague, wrong "
                        + "or missing nuance.",
                "Ask a peer or supervisor for one specific observation about your listening or explanations after a "
                        + "patient-facing session.",
                "Write your view of a communication scenario first; then look at how the AI version differs in tone "
                        + "and judgement.",
                "Ask about one skill at a time, and write the feedback down the same day so you can show how you acted "
                        + "on it.",
                "Volunteering in a care setting or a health-related society can add experience of talking with people "
                        + "in different situations.",
                "Note what published guidance says about AI and records, confidentiality and checking information, "
                        + "and discuss it with your supervisor.",
                "Short written reflections after significant conversations or decisions make good examples, as long "
                        + "as patient details are anonymised.",
                "Include skills logs, supervisor feedback and reflections that show your process, with patient details "
                        + "removed.");
        return new UniversityPaidNarrative(
                "Your course combines science you can rehearse with AI support, such as anatomy, physiology and "
                        + "pharmacology, with care that depends on your own skill with patients, families and "
                        + "colleagues. AI may help you revise mechanisms, test your recall and organise reading, but it "
                        + "is never a source of clinical authority.\n\n"
                        + "The sections below show how AI could support learning in each analysed module, what you "
                        + "must be able to do yourself, and where placements, simulation and communication make your "
                        + "own practice central.",
                "Read together, moderate Working Alongside AI and high Human and Practical Capability describe a "
                        + "course where AI can support study while practice with people carries most weight; moderate "
                        + "Transferable Capability reflects how closely the course prepares you for nursing practice.",
                "Your analysed modules fall into two groups. The science modules are knowledge-heavy and lend "
                        + "themselves to AI-supported revision, as do the reading-based parts of evidence-based "
                        + "practice. Placements, simulation and communication are where judgement and skill with "
                        + "people lead, and AI's role there is preparation and reflection only.",
                List.of(
                        new ModuleLearningInsight("module-2",
                                "Much of this module is understanding how body systems work and recalling them "
                                        + "accurately, so AI can be a useful explainer and quizzer, provided you check "
                                        + "what it says against your course material.",
                                List.of("Explaining a difficult process, such as the cardiac cycle or renal filtration, "
                                                + "in several different ways",
                                        "Generating recall questions on a body system you have just studied",
                                        "Contrasting related processes, such as sympathetic and parasympathetic "
                                                + "responses",
                                        "Creating short revision scenarios that link a physiological change to its "
                                                + "signs"),
                                List.of("Explaining core mechanisms accurately without notes",
                                        "Recognising when an explanation sounds plausible but is physiologically wrong"),
                                "Draw and label one body system from memory, then ask AI to quiz you on it. Check every "
                                        + "answer you were unsure of against your textbook or lecture notes, not "
                                        + "against the AI."),
                        new ModuleLearningInsight("module-4",
                                "Pharmacology asks for accurate knowledge of how medicines act and are managed safely, "
                                        + "so AI can help you organise and test that knowledge, but it is never a "
                                        + "substitute for approved course material or clinical guidance.",
                                List.of("Setting revision questions on drug classes and their mechanisms of action",
                                        "Describing how a drug class acts on its target, which you then check against "
                                                + "your course material",
                                        "Building a revision table of side-effect profiles for related drug classes",
                                        "Creating hypothetical medicines-management scenarios to discuss in a study "
                                                + "group"),
                                List.of("Knowing drug classes, actions and key cautions accurately",
                                        "Working practice drug calculations by hand, using the method your course "
                                                + "teaches",
                                        "Checking information against approved course and professional sources, never "
                                                + "treating AI output as authoritative guidance"),
                                "Write your own summary of one drug class, then ask AI to set questions about it. "
                                        + "Answer them, and verify each answer against the course notes and approved "
                                        + "references your course provides, noting any AI errors."),
                        new ModuleLearningInsight("module-6",
                                "Evidence-based practice is about judging the quality of research and applying it to "
                                        + "care, so AI is helpful for organising reading and rehearsing appraisal, "
                                        + "while the appraisal and conclusions stay yours.",
                                List.of("Turning a clinical question into search terms and synonyms",
                                        "Weighing the strengths and weaknesses of two study designs",
                                        "Drafting critical-appraisal questions to ask of a paper you have read",
                                        "Structuring your literature notes into a comparison table"),
                                List.of("Appraising the quality of a study yourself",
                                        "Checking that every source and quotation is real and says what is claimed",
                                        "Reaching a defensible conclusion and stating its limitations"),
                                "Appraise one paper yourself using the framework your course teaches, then ask AI for "
                                        + "its appraisal. Compare the two, and check any point where they differ "
                                        + "against the paper itself."),
                        new ModuleLearningInsight("module-1",
                                "This module introduces the values, standards and person-centred care that shape "
                                        + "nursing, so AI can help you prepare for discussion and reflection, while "
                                        + "understanding and applying those standards stays yours.",
                                List.of("Preparing questions on person-centred care to bring to a seminar",
                                        "Exploring different perspectives on a care scenario before you reflect on it "
                                                + "in writing",
                                        "Summarising a reading on professional values so you can compare it with your "
                                                + "own notes"),
                                List.of("Applying professional standards to a care situation in your own words",
                                        "Writing reflections that draw on your own practice experience"),
                                "Write a short reflection on one care experience first, then ask AI what questions a "
                                        + "mentor might ask about it. Answer those questions yourself, without AI.")),
                List.of(new HumanCapabilityInsight("module-7",
                                "On placement you care for real patients under supervision, so your assessment, "
                                        + "communication and judgement are built and observed in practice. That "
                                        + "experience grows only by being there and reflecting on it with your "
                                        + "supervisors.",
                                "Be able to carry out, explain and reflect on the care you give, with your supervisor's "
                                        + "feedback rather than AI as your guide."),
                        new HumanCapabilityInsight("module-3",
                                "Simulation is where you practise hands-on skills and communication safely before "
                                        + "placement, and the learning comes from your own repetitions, mistakes and "
                                        + "feedback.",
                                "Be able to perform each skill you practise to the standard your course sets, and talk "
                                        + "through what you are doing and why."),
                        new HumanCapabilityInsight("module-5",
                                "Compassionate communication depends on noticing emotion and context in the person in "
                                        + "front of you and adapting as you go, which is learned through real "
                                        + "conversations and reflection.",
                                "Be able to hold a difficult conversation with a patient or relative, listening and "
                                        + "adapting to them, without a script."),
                        new HumanCapabilityInsight("module-1",
                                "This module sets out how you apply professional values with patients and colleagues, "
                                        + "so your own judgement about dignity, consent and advocacy needs to grow "
                                        + "from the start.",
                                "Be able to explain how a professional standard applies to a real care situation and "
                                        + "justify what you would do.")),
                List.of(new SkillInsight("skill-professional-judgement",
                                "Several of your modules appear shaped by professional and clinical standards, and "
                                        + "applying them is a judgement you make yourself.",
                                List.of("Checking AI explanations of medicines against approved course material",
                                        "Explaining how a professional standard applies in a placement situation",
                                        "Recognising when a plausible-sounding answer would be unsafe in practice")),
                        new SkillInsight("skill-communication-and-collaboration",
                                "Much of your course involves working with patients, families and colleagues.",
                                List.of("Practising handovers and explanations in simulation",
                                        "Asking for feedback on how you communicate on placement")),
                        new SkillInsight("skill-critical-evaluation",
                                "Reading, appraising and reflecting are where your own reasoning develops.",
                                List.of("Appraising research for evidence-based practice before comparing views",
                                        "Forming your own view of a care scenario before consulting AI")),
                        new SkillInsight("skill-practical-application",
                                "Hands-on skill is built through practice and feedback.",
                                List.of("Repeating clinical skills in simulation until they are secure",
                                        "Keeping a log of skills practised on placement")),
                        new SkillInsight("skill-technical-foundations",
                                "Your science modules give you the knowledge to recognise when an explanation is wrong.",
                                List.of("Understanding anatomy and physiology well enough to spot incorrect "
                                                + "explanations",
                                        "Checking pharmacology information against approved course material",
                                        "Understanding clinical concepts independently of AI summaries"))),
                notes(context, notes),
                new AiStudyStrategy(
                        List.of("Explaining physiological and pharmacological mechanisms in different ways",
                                "Generating recall and revision questions after lectures",
                                "Organising reading and appraisal notes for evidence-based practice"),
                        List.of("Recalling anatomy, physiology and drug knowledge accurately from memory",
                                "Practising clinical and communication skills in simulation and on placement",
                                "Writing reflections from your own experience"),
                        List.of("Check medicines and clinical information against approved course material and "
                                        + "professional guidance",
                                "Check that every reference and quotation AI gives exists and says what is claimed"),
                        List.of("Keep skills logs and supervisor feedback from placements",
                                "Keep drafts and reflections that show how your own thinking developed"),
                        List.of("A portfolio of anonymised reflections, feedback and skills practised",
                                "Examples of decisions and communication you handled yourself")),
                "Studying nursing alongside AI works best when AI helps you revise and question, while care, judgement "
                        + "and skill with people stay firmly in your own hands.");
    }

    private static List<ActionPlanNote> notes(UniversityPaidNarrativeContext context, List<String> explanations) {
        List<String> ids = context.actionIds();
        if (ids.size() != explanations.size()) {
            throw new IllegalStateException("the calibration notes expect " + explanations.size() + " actions, the "
                    + "guidance has " + ids);
        }
        List<ActionPlanNote> notes = new ArrayList<>();
        for (int index = 0; index < ids.size(); index++) {
            notes.add(new ActionPlanNote(ids.get(index), explanations.get(index)));
        }
        return notes;
    }

    private static void expect(List<String> actual, String... expected) {
        if (!actual.equals(List.of(expected))) {
            throw new IllegalStateException("the calibration draft expects " + List.of(expected) + ", the guidance "
                    + "selected " + actual);
        }
    }
}
