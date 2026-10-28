package com.chikere.futureai.livesmoke;

import org.springframework.beans.BeanWrapperImpl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Every live smoke case, in one place. To add a case, add one entry to {@link #CASES}: the dashboard, the
 * prefill route and the tests pick it up from here.
 *
 * <p>Professional cases are the agreed live regression inputs, plus four Stage 4C responsibility-calibration cases
 * (electrician, secondary-school teacher, solicitor, customer-service adviser). University and A-level cases are taken from
 * inputs already in this repository (the example pages and existing tests); none is invented.</p>
 */
public final class LiveSmokeCaseCatalog {

    /**
     * The seven representative University courses, exactly as frozen for Stage 5B calibration (no institution, so no
     * real course is implied). Each lists more than eight modules, so each exercises the module-selection step.
     */
    private static final String CALIBRATION_SOURCE = "University calibration input (UniversityBaselineCases)";

    private static final List<LiveSmokeCase> CASES = List.of(
            new LiveSmokeCase("registered-nurse", LiveSmokeJourney.PROFESSIONAL, "Registered Nurse",
                    "Agreed live regression case", fields(
                            "profession", "Registered Nurse",
                            "workDescription", "I care for patients on a hospital ward. I monitor patients, administer "
                                    + "medication, record observations, assess changes in their condition and decide "
                                    + "when concerns need to be escalated to doctors or other clinical staff. I explain "
                                    + "treatment and care to patients and their families, coordinate with healthcare "
                                    + "professionals and respond to unexpected situations during a shift.",
                            "responsibilities", lines(
                                    "Monitor patients and assess changes in their condition",
                                    "Administer medication safely",
                                    "Record observations and clinical information",
                                    "Escalate concerns to doctors and senior clinical staff",
                                    "Support patients and communicate with families",
                                    "Coordinate care with other healthcare professionals",
                                    "Respond to emergencies and unexpected situations",
                                    "Follow clinical procedures and safety requirements"),
                            "skills", lines("Clinical judgement", "Patient care", "Communication", "Observation",
                                    "Decision making", "Teamwork", "Medication management", "Empathy",
                                    "Risk assessment", "Working under pressure"),
                            "yearsOfExperience", 8)),
            new LiveSmokeCase("java-software-developer", LiveSmokeJourney.PROFESSIONAL, "Java Software Developer",
                    "Agreed live regression case", fields(
                            "profession", "Java Software Developer",
                            "workDescription", "I design and build backend services using Java and Spring Boot. I "
                                    + "discuss requirements with product managers and business analysts, review "
                                    + "existing code, implement new features, fix defects and investigate production "
                                    + "issues. I also review code written by other developers, make technical "
                                    + "decisions about APIs and data models, and work with DevOps engineers when "
                                    + "deploying services.",
                            "responsibilities", lines(
                                    "Design and develop backend services",
                                    "Review pull requests and technical designs",
                                    "Investigate and resolve production incidents",
                                    "Discuss requirements with product and business teams",
                                    "Make decisions about APIs, integrations and data models",
                                    "Mentor less experienced developers",
                                    "Support releases and deployments",
                                    "Improve automated tests and code quality"),
                            "skills", lines("Java", "Spring Boot", "REST APIs", "SQL", "AWS", "Problem solving",
                                    "Software design", "Code review", "Communication", "Technical decision making",
                                    "Mentoring"),
                            "yearsOfExperience", 12)),
            new LiveSmokeCase("management-accountant", LiveSmokeJourney.PROFESSIONAL, "Management Accountant",
                    "Agreed live regression case", fields(
                            "profession", "Management Accountant",
                            "workDescription", "I prepare monthly management accounts, analyse financial performance "
                                    + "and explain significant differences between budgets and actual results. I work "
                                    + "with department managers to understand spending, prepare forecasts and support "
                                    + "budgeting decisions. I use spreadsheets and finance systems to analyse data, "
                                    + "produce reports and identify areas where costs or performance need attention.",
                            "responsibilities", lines(
                                    "Prepare monthly management accounts",
                                    "Analyse budgets against actual financial results",
                                    "Produce financial reports for management",
                                    "Prepare forecasts and support annual budgeting",
                                    "Investigate unusual costs or financial variances",
                                    "Meet department managers to discuss financial performance",
                                    "Provide financial information to support business decisions",
                                    "Maintain accurate financial records and reporting processes"),
                            "skills", lines("Financial analysis", "Management accounting", "Excel", "Budgeting",
                                    "Forecasting", "Data analysis", "Communication", "Attention to detail",
                                    "Commercial awareness", "Problem solving"),
                            "yearsOfExperience", 7)),
            new LiveSmokeCase("electrician", LiveSmokeJourney.PROFESSIONAL, "Electrician",
                    "Stage 4C responsibility calibration case", fields(
                            "profession", "Electrician",
                            "workDescription", "I install, inspect, test and repair electrical systems in homes and "
                                    + "commercial premises. I diagnose electrical faults, read technical drawings, "
                                    + "follow electrical safety requirements, complete technical documentation and "
                                    + "explain faults and repair options to customers.",
                            "responsibilities", lines(
                                    "Install electrical wiring and equipment",
                                    "Inspect and test electrical systems",
                                    "Diagnose electrical faults",
                                    "Repair faulty wiring and components",
                                    "Read technical drawings and specifications",
                                    "Explain faults and repair options to customers",
                                    "Complete safety documentation",
                                    "Follow electrical safety regulations"),
                            "skills", lines("Electrical installation", "Fault diagnosis", "Electrical testing",
                                    "Safety procedures", "Technical drawings", "Problem solving",
                                    "Customer communication", "Risk awareness"),
                            "yearsOfExperience", 8)),
            new LiveSmokeCase("secondary-school-teacher", LiveSmokeJourney.PROFESSIONAL, "Secondary-school Teacher",
                    "Stage 4C responsibility calibration case", fields(
                            "profession", "Secondary-school Teacher",
                            "workDescription", "I plan and teach lessons, explain concepts to students, assess their "
                                    + "work and give feedback. I manage classroom behaviour, adapt teaching to different "
                                    + "needs, communicate with parents and colleagues, and maintain safeguarding and "
                                    + "academic records.",
                            "responsibilities", lines(
                                    "Plan lessons",
                                    "Teach lessons to students",
                                    "Explain difficult concepts",
                                    "Assess student work",
                                    "Give students feedback",
                                    "Manage classroom behaviour",
                                    "Communicate with parents",
                                    "Maintain safeguarding and academic records"),
                            "skills", lines("Teaching", "Lesson planning", "Communication", "Assessment",
                                    "Classroom management", "Subject knowledge", "Safeguarding", "Adaptability",
                                    "Empathy", "Feedback"),
                            "yearsOfExperience", 7)),
            new LiveSmokeCase("solicitor", LiveSmokeJourney.PROFESSIONAL, "Solicitor",
                    "Stage 4C responsibility calibration case", fields(
                            "profession", "Solicitor",
                            "workDescription", "I advise clients on legal matters, research relevant law and precedent, "
                                    + "review and draft contracts and other legal documents, assess legal risks, "
                                    + "negotiate with other parties and manage confidential client information. I work "
                                    + "within professional, legal and ethical obligations.",
                            "responsibilities", lines(
                                    "Review contracts and legal documents",
                                    "Research relevant law and precedent",
                                    "Draft legal documents and correspondence",
                                    "Advise clients on legal options",
                                    "Negotiate with other parties",
                                    "Assess legal risks",
                                    "Manage confidential client information",
                                    "Ensure work complies with professional obligations"),
                            "skills", lines("Legal research", "Legal drafting", "Legal analysis", "Client advice",
                                    "Negotiation", "Risk assessment", "Communication", "Confidentiality",
                                    "Professional judgement", "Attention to detail"),
                            "yearsOfExperience", 8)),
            new LiveSmokeCase("customer-service-adviser", LiveSmokeJourney.PROFESSIONAL, "Customer-service Adviser",
                    "Stage 4C responsibility calibration case", fields(
                            "profession", "Customer-service Adviser",
                            "workDescription", "I respond to customer enquiries, explain products and services, "
                                    + "investigate account problems, resolve straightforward complaints and escalate "
                                    + "more complex issues. I update customer records, handle sensitive customer "
                                    + "information and sometimes communicate decisions that customers may find "
                                    + "difficult.",
                            "responsibilities", lines(
                                    "Respond to customer enquiries",
                                    "Explain products and services",
                                    "Resolve straightforward complaints",
                                    "Investigate account problems",
                                    "Escalate complex complaints",
                                    "Update customer records",
                                    "Handle sensitive customer information",
                                    "Communicate difficult decisions to customers"),
                            "skills", lines("Customer communication", "Complaint handling", "Problem solving",
                                    "Product knowledge", "Record keeping", "Data handling", "Listening",
                                    "Conflict resolution", "Empathy", "Attention to detail"),
                            "yearsOfExperience", 5)),
            new LiveSmokeCase("computer-science", LiveSmokeJourney.UNIVERSITY, "BSc Computer Science",
                    "University example page (university-example.html)", fields(
                            "courseTitle", "BSc Computer Science",
                            "courseDescription", "The course covers programming, software engineering, databases, "
                                    + "algorithms, computer systems, artificial intelligence and data analysis. "
                                    + "Students learn through lectures, practical laboratory sessions, individual "
                                    + "programming assignments and group software projects. The course also includes "
                                    + "a final-year project where students research, design and build a substantial "
                                    + "technical solution.",
                            "keyModules", lines("Programming and Software Development", "Algorithms and Data Structures",
                                    "Databases", "Software Engineering", "Computer Systems", "Artificial Intelligence",
                                    "Machine Learning", "Cybersecurity", "Data Science", "Final-Year Project"),
                            "skillsDeveloped", lines("Programming", "Problem solving", "Analytical reasoning",
                                    "Software design", "Data analysis", "Research", "Teamwork",
                                    "Technical communication", "Independent learning", "Project work"),
                            "preferredCareerAreas", lines("Software development", "AI and machine learning",
                                    "Cybersecurity", "Technology consulting"),
                            "institution", "University of Manchester",
                            "country", "United Kingdom")),
            new LiveSmokeCase("economics", LiveSmokeJourney.UNIVERSITY, "Economics",
                    "UniversityJevJudgmentServiceTest input", fields(
                            "courseTitle", "Economics",
                            "courseDescription", "Micro, macro, econometrics and a quantitative dissertation.",
                            "keyModules", lines("Econometrics"),
                            "skillsDeveloped", lines("Statistical analysis"),
                            "preferredCareerAreas", lines("Policy analysis"),
                            "country", "United Kingdom")),
            new LiveSmokeCase("history", LiveSmokeJourney.UNIVERSITY, "History",
                    "UniversityJevStateMapperTest input", fields(
                            "courseTitle", "History",
                            "courseDescription", "Sources, historiography, essays and an independent dissertation.",
                            "keyModules", lines("Early modern Europe"),
                            "skillsDeveloped", lines("Argument"),
                            "institution", "A UK university",
                            "country", "United Kingdom")),
            new LiveSmokeCase("calibration-computer-science", LiveSmokeJourney.UNIVERSITY, "BSc Computer Science",
                    CALIBRATION_SOURCE, fields(
                            "courseTitle", "BSc Computer Science",
                            "courseDescription", "The course covers programming, algorithms, data structures, "
                                    + "databases, computer systems, networks and software engineering, with options "
                                    + "in artificial intelligence and cybersecurity. Teaching combines lectures, "
                                    + "weekly laboratory programming sessions, individual coursework and a "
                                    + "second-year group software project. The final year includes an individual "
                                    + "project in which students research, design, build and evaluate a substantial "
                                    + "piece of software.",
                            "keyModules", lines("Programming Fundamentals", "Object-Oriented Programming",
                                    "Algorithms and Data Structures", "Discrete Mathematics", "Databases",
                                    "Computer Systems and Architecture", "Operating Systems and Networks",
                                    "Software Engineering Group Project", "Artificial Intelligence", "Cybersecurity",
                                    "Final-Year Individual Project"),
                            "skillsDeveloped", lines("Programming", "Problem solving", "Software design",
                                    "Mathematical reasoning", "Testing", "Teamwork", "Technical writing",
                                    "Independent research"),
                            "preferredCareerAreas", lines("Software development", "Data and AI", "Cybersecurity"),
                            "country", "United Kingdom")),
            new LiveSmokeCase("calibration-business-management", LiveSmokeJourney.UNIVERSITY, "BA Business Management",
                    CALIBRATION_SOURCE, fields(
                            "courseTitle", "BA Business Management",
                            "courseDescription", "The course introduces how organisations are run, covering "
                                    + "management, marketing, finance and accounting, human resource management, "
                                    + "operations and strategy. Students learn through lectures, seminars, case "
                                    + "studies, group presentations and reports. An optional placement year is "
                                    + "available, and the final year includes a strategy module and a research-based "
                                    + "dissertation or consultancy project.",
                            "keyModules", lines("Principles of Management", "Introduction to Marketing",
                                    "Financial and Management Accounting", "Organisational Behaviour",
                                    "Human Resource Management", "Operations Management", "Business Analytics",
                                    "Entrepreneurship", "Strategic Management", "Dissertation or Consultancy Project"),
                            "skillsDeveloped", lines("Business analysis", "Report writing", "Presentation",
                                    "Teamwork", "Basic financial analysis", "Decision making", "Research"),
                            "preferredCareerAreas", lines("Management", "Marketing", "Human resources", "Consulting"),
                            "country", "United Kingdom")),
            new LiveSmokeCase("calibration-nursing", LiveSmokeJourney.UNIVERSITY, "BSc Nursing (Adult)",
                    CALIBRATION_SOURCE, fields(
                            "courseTitle", "BSc Nursing (Adult)",
                            "courseDescription", "A three-year pre-registration course leading to eligibility to "
                                    + "apply for registration as an adult nurse. Half of the course is spent on "
                                    + "clinical placements in hospital and community settings under the supervision "
                                    + "of registered nurses. University study covers anatomy and physiology, "
                                    + "pharmacology, evidence-based practice, communication, and professional, legal "
                                    + "and ethical standards, with clinical skills practised in simulation suites "
                                    + "before placement.",
                            "keyModules", lines("Foundations of Nursing Practice", "Anatomy and Physiology",
                                    "Clinical Skills and Simulation", "Pharmacology and Medicines Management",
                                    "Communication and Compassionate Care", "Evidence-Based Practice",
                                    "Acute and Long-Term Conditions", "Professional, Legal and Ethical Practice",
                                    "Leadership and Management of Care", "Clinical Placements"),
                            "skillsDeveloped", lines("Patient assessment", "Clinical skills", "Communication",
                                    "Care planning", "Medicines management", "Teamwork", "Professional judgement",
                                    "Record keeping"),
                            "preferredCareerAreas", lines("Adult nursing", "Community nursing"),
                            "country", "United Kingdom")),
            new LiveSmokeCase("calibration-law", LiveSmokeJourney.UNIVERSITY, "LLB Law",
                    CALIBRATION_SOURCE, fields(
                            "courseTitle", "LLB Law",
                            "courseDescription", "The course covers the core areas of English law, including "
                                    + "contract, tort, criminal, public, land, equity and trusts, and EU and "
                                    + "international law. Students learn through lectures and small-group tutorials, "
                                    + "and develop legal research, analysis and writing through problem questions and "
                                    + "essays. Mooting, a law clinic option and a final-year dissertation give "
                                    + "opportunities to apply legal reasoning.",
                            "keyModules", lines("Legal Method and Skills", "Contract Law", "Law of Tort",
                                    "Criminal Law", "Constitutional and Administrative Law", "Land Law",
                                    "Equity and Trusts", "EU and International Law", "Law Clinic", "Dissertation"),
                            "skillsDeveloped", lines("Legal research", "Legal analysis", "Written argument",
                                    "Oral advocacy", "Critical reading", "Attention to detail", "Problem solving"),
                            "preferredCareerAreas", lines("Legal practice", "Policy", "Compliance"),
                            "country", "United Kingdom")),
            new LiveSmokeCase("calibration-graphic-design", LiveSmokeJourney.UNIVERSITY, "BA Graphic Design",
                    CALIBRATION_SOURCE, fields(
                            "courseTitle", "BA Graphic Design",
                            "courseDescription", "A studio-based course in visual communication. Students develop "
                                    + "typography, layout, branding, illustration, motion and digital design through "
                                    + "briefs set by tutors and, in later years, by external clients and "
                                    + "competitions. Work is reviewed in group critiques, and the course covers "
                                    + "design history, research methods and professional practice. The final year "
                                    + "centres on a self-directed major project and a public portfolio show.",
                            "keyModules", lines("Design Fundamentals", "Typography", "Visual Identity and Branding",
                                    "Illustration", "Digital and Interaction Design", "Motion Graphics",
                                    "Design History and Theory", "Live Briefs", "Professional Practice",
                                    "Final Major Project"),
                            "skillsDeveloped", lines("Visual communication", "Typography", "Concept development",
                                    "Design software", "Presenting and critiquing work", "Working to a brief",
                                    "Portfolio building"),
                            "preferredCareerAreas", lines("Graphic design", "Branding", "Digital design"),
                            "country", "United Kingdom")),
            new LiveSmokeCase("calibration-mechanical-engineering", LiveSmokeJourney.UNIVERSITY,
                    "BEng Mechanical Engineering", CALIBRATION_SOURCE, fields(
                            "courseTitle", "BEng Mechanical Engineering",
                            "courseDescription", "An engineering degree covering mechanics, thermodynamics, fluid "
                                    + "mechanics, materials, mechanical design and control systems, built on "
                                    + "engineering mathematics. Students carry out laboratory experiments, use "
                                    + "computer-aided design and simulation, and complete design-and-build group "
                                    + "projects. The course is designed to meet the academic requirements for "
                                    + "professional engineering registration and ends with an individual project.",
                            "keyModules", lines("Engineering Mathematics", "Statics and Dynamics", "Thermodynamics",
                                    "Fluid Mechanics", "Materials Science", "Mechanical Design and CAD",
                                    "Control Systems", "Manufacturing Processes",
                                    "Engineering Design-and-Build Project", "Individual Project"),
                            "skillsDeveloped", lines("Mathematical modelling", "Engineering design",
                                    "Laboratory work", "CAD and simulation", "Problem solving", "Teamwork",
                                    "Technical reporting"),
                            "preferredCareerAreas", lines("Mechanical engineering", "Manufacturing", "Energy"),
                            "country", "United Kingdom")),
            new LiveSmokeCase("calibration-english-literature", LiveSmokeJourney.UNIVERSITY, "BA English Literature",
                    CALIBRATION_SOURCE, fields(
                            "courseTitle", "BA English Literature",
                            "courseDescription", "The course studies literature in English from the medieval "
                                    + "period to the present, including poetry, drama and the novel, alongside "
                                    + "literary theory and criticism. Students learn through lectures and seminar "
                                    + "discussion and are assessed mainly through essays, close-reading exercises and "
                                    + "some examinations. Optional modules include creative writing, and the final "
                                    + "year includes an independent dissertation.",
                            "keyModules", lines("Approaches to Literature", "Medieval and Renaissance Literature",
                                    "Shakespeare", "Romantic and Victorian Literature", "Modernism",
                                    "Contemporary Fiction", "Literary Theory", "Creative Writing", "Dissertation"),
                            "skillsDeveloped", lines("Close reading", "Essay writing", "Argument", "Interpretation",
                                    "Research", "Seminar discussion", "Independent study"),
                            "preferredCareerAreas", lines("Publishing", "Journalism", "Teaching"),
                            "country", "United Kingdom")),
            new LiveSmokeCase("maths-economics-geography", LiveSmokeJourney.A_LEVEL,
                    "Mathematics, Economics and Geography", "A-level example page (a-level-example.html)", fields(
                            "selectedSubjects", lines("Mathematics", "Economics", "Geography"),
                            "predictedGrades", lines("Mathematics: A", "Economics: B", "Geography: A"),
                            "strengthsAndInterests", lines("Solving maths problems",
                                    "Understanding how businesses and economies work", "Working with data and graphs",
                                    "Researching environmental and global issues",
                                    "Explaining ideas in written arguments"),
                            "preferredDegreeAreas", lines("Economics", "Geography", "Business and Management",
                                    "Data-related courses"),
                            "preferredCareerAreas", lines("Finance", "Economic analysis", "Environmental consulting",
                                    "Business", "Technology"),
                            "country", "United Kingdom",
                            "additionalContext", "I'm interested in keeping both business and environmental options "
                                    + "open. I'm also considering Computer Science, but I'm not yet sure whether I "
                                    + "want to take it instead of Geography.")),
            new LiveSmokeCase("mathematics-physics", LiveSmokeJourney.A_LEVEL, "Mathematics and Physics",
                    "ALevelJevStateMapperTest input", fields(
                            "selectedSubjects", lines("Mathematics", "Physics"),
                            "predictedGrades", lines("Mathematics: A"),
                            "strengthsAndInterests", lines("Problem solving"),
                            "preferredDegreeAreas", lines("Engineering"),
                            "preferredCareerAreas", lines("Civil engineering"),
                            "country", "United Kingdom",
                            "additionalContext", "Considering a placement year.")),
            new LiveSmokeCase("english-history-politics", LiveSmokeJourney.A_LEVEL,
                    "English Literature, History and Politics (subjects only)",
                    "ALevelAssessmentFormMapperTest input (an undecided student)", fields(
                            "selectedSubjects", lines("English Literature", "History", "Politics"))));

    public List<LiveSmokeCase> all() {
        return CASES;
    }

    public List<LiveSmokeCase> forJourney(LiveSmokeJourney journey) {
        return CASES.stream().filter(value -> value.journey() == journey).toList();
    }

    public Optional<LiveSmokeCase> find(LiveSmokeJourney journey, String id) {
        return CASES.stream().filter(value -> value.journey() == journey && value.id().equals(id)).findFirst();
    }

    /** The journey's normal form DTO, populated exactly as if the visitor had typed the case's inputs. */
    public Object form(LiveSmokeCase smokeCase) {
        Object form = smokeCase.journey().newForm();
        new BeanWrapperImpl(form).setPropertyValues(smokeCase.fields());
        return form;
    }

    private static Map<String, Object> fields(Object... namesAndValues) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (int index = 0; index < namesAndValues.length; index += 2) {
            fields.put((String) namesAndValues[index], namesAndValues[index + 1]);
        }
        return fields;
    }

    private static String lines(String... values) {
        return String.join("\n", values);
    }
}
