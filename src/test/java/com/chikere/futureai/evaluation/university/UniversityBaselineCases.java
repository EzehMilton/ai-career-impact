package com.chikere.futureai.evaluation.university;

import com.chikere.futureai.assessment.domain.UniversityAssessmentState;

import java.util.List;
import java.util.Optional;

/**
 * Stage 5B: the seven representative University courses. Plausible, generic UK course descriptions written in the
 * fields the form accepts; no institution, so no real course is implied. They are not tuned towards any score.
 */
final class UniversityBaselineCases {

    record BaselineCase(String id, UniversityAssessmentState state) {
    }

    private static final Optional<String> UK = Optional.of("United Kingdom");

    static final List<BaselineCase> ALL = List.of(
            new BaselineCase("computer-science", new UniversityAssessmentState("BSc Computer Science",
                    "The course covers programming, algorithms, data structures, databases, computer systems, "
                            + "networks and software engineering, with options in artificial intelligence and "
                            + "cybersecurity. Teaching combines lectures, weekly laboratory programming sessions, "
                            + "individual coursework and a second-year group software project. The final year "
                            + "includes an individual project in which students research, design, build and "
                            + "evaluate a substantial piece of software.",
                    List.of("Programming Fundamentals", "Object-Oriented Programming", "Algorithms and Data Structures",
                            "Discrete Mathematics", "Databases", "Computer Systems and Architecture",
                            "Operating Systems and Networks", "Software Engineering Group Project",
                            "Artificial Intelligence", "Cybersecurity", "Final-Year Individual Project"),
                    List.of("Programming", "Problem solving", "Software design", "Mathematical reasoning", "Testing",
                            "Teamwork", "Technical writing", "Independent research"),
                    List.of("Software development", "Data and AI", "Cybersecurity"), Optional.empty(), UK)),
            new BaselineCase("business-management", new UniversityAssessmentState("BA Business Management",
                    "The course introduces how organisations are run, covering management, marketing, finance and "
                            + "accounting, human resource management, operations and strategy. Students learn "
                            + "through lectures, seminars, case studies, group presentations and reports. An "
                            + "optional placement year is available, and the final year includes a strategy "
                            + "module and a research-based dissertation or consultancy project.",
                    List.of("Principles of Management", "Introduction to Marketing", "Financial and Management Accounting",
                            "Organisational Behaviour", "Human Resource Management", "Operations Management",
                            "Business Analytics", "Entrepreneurship", "Strategic Management",
                            "Dissertation or Consultancy Project"),
                    List.of("Business analysis", "Report writing", "Presentation", "Teamwork",
                            "Basic financial analysis", "Decision making", "Research"),
                    List.of("Management", "Marketing", "Human resources", "Consulting"), Optional.empty(), UK)),
            new BaselineCase("nursing", new UniversityAssessmentState("BSc Nursing (Adult)",
                    "A three-year pre-registration course leading to eligibility to apply for registration as an "
                            + "adult nurse. Half of the course is spent on clinical placements in hospital and "
                            + "community settings under the supervision of registered nurses. University study "
                            + "covers anatomy and physiology, pharmacology, evidence-based practice, communication, "
                            + "and professional, legal and ethical standards, with clinical skills practised in "
                            + "simulation suites before placement.",
                    List.of("Foundations of Nursing Practice", "Anatomy and Physiology", "Clinical Skills and Simulation",
                            "Pharmacology and Medicines Management", "Communication and Compassionate Care",
                            "Evidence-Based Practice", "Acute and Long-Term Conditions",
                            "Professional, Legal and Ethical Practice", "Leadership and Management of Care",
                            "Clinical Placements"),
                    List.of("Patient assessment", "Clinical skills", "Communication", "Care planning",
                            "Medicines management", "Teamwork", "Professional judgement", "Record keeping"),
                    List.of("Adult nursing", "Community nursing"), Optional.empty(), UK)),
            new BaselineCase("law", new UniversityAssessmentState("LLB Law",
                    "The course covers the core areas of English law, including contract, tort, criminal, public, "
                            + "land, equity and trusts, and EU and international law. Students learn through "
                            + "lectures and small-group tutorials, and develop legal research, analysis and "
                            + "writing through problem questions and essays. Mooting, a law clinic option and a "
                            + "final-year dissertation give opportunities to apply legal reasoning.",
                    List.of("Legal Method and Skills", "Contract Law", "Law of Tort", "Criminal Law",
                            "Constitutional and Administrative Law", "Land Law", "Equity and Trusts",
                            "EU and International Law", "Law Clinic", "Dissertation"),
                    List.of("Legal research", "Legal analysis", "Written argument", "Oral advocacy",
                            "Critical reading", "Attention to detail", "Problem solving"),
                    List.of("Legal practice", "Policy", "Compliance"), Optional.empty(), UK)),
            new BaselineCase("graphic-design", new UniversityAssessmentState("BA Graphic Design",
                    "A studio-based course in visual communication. Students develop typography, layout, branding, "
                            + "illustration, motion and digital design through briefs set by tutors and, in later "
                            + "years, by external clients and competitions. Work is reviewed in group critiques, "
                            + "and the course covers design history, research methods and professional practice. "
                            + "The final year centres on a self-directed major project and a public portfolio show.",
                    List.of("Design Fundamentals", "Typography", "Visual Identity and Branding", "Illustration",
                            "Digital and Interaction Design", "Motion Graphics", "Design History and Theory",
                            "Live Briefs", "Professional Practice", "Final Major Project"),
                    List.of("Visual communication", "Typography", "Concept development", "Design software",
                            "Presenting and critiquing work", "Working to a brief", "Portfolio building"),
                    List.of("Graphic design", "Branding", "Digital design"), Optional.empty(), UK)),
            new BaselineCase("mechanical-engineering", new UniversityAssessmentState("BEng Mechanical Engineering",
                    "An engineering degree covering mechanics, thermodynamics, fluid mechanics, materials, "
                            + "mechanical design and control systems, built on engineering mathematics. Students "
                            + "carry out laboratory experiments, use computer-aided design and simulation, and "
                            + "complete design-and-build group projects. The course is designed to meet the "
                            + "academic requirements for professional engineering registration and ends with an "
                            + "individual project.",
                    List.of("Engineering Mathematics", "Statics and Dynamics", "Thermodynamics", "Fluid Mechanics",
                            "Materials Science", "Mechanical Design and CAD", "Control Systems",
                            "Manufacturing Processes", "Engineering Design-and-Build Project", "Individual Project"),
                    List.of("Mathematical modelling", "Engineering design", "Laboratory work", "CAD and simulation",
                            "Problem solving", "Teamwork", "Technical reporting"),
                    List.of("Mechanical engineering", "Manufacturing", "Energy"), Optional.empty(), UK)),
            new BaselineCase("english-literature", new UniversityAssessmentState("BA English Literature",
                    "The course studies literature in English from the medieval period to the present, including "
                            + "poetry, drama and the novel, alongside literary theory and criticism. Students "
                            + "learn through lectures and seminar discussion and are assessed mainly through "
                            + "essays, close-reading exercises and some examinations. Optional modules include "
                            + "creative writing, and the final year includes an independent dissertation.",
                    List.of("Approaches to Literature", "Medieval and Renaissance Literature", "Shakespeare",
                            "Romantic and Victorian Literature", "Modernism", "Contemporary Fiction",
                            "Literary Theory", "Creative Writing", "Dissertation"),
                    List.of("Close reading", "Essay writing", "Argument", "Interpretation", "Research",
                            "Seminar discussion", "Independent study"),
                    List.of("Publishing", "Journalism", "Teaching"), Optional.empty(), UK)));

    private UniversityBaselineCases() {
    }
}
