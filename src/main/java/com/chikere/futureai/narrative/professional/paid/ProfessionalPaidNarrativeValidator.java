package com.chikere.futureai.narrative.professional.paid;

import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrative.AdaptationStrategy;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrative.HumanValueInsight;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrative.ItemExplanation;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrative.OpportunityInsight;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrative.SkillInsight;
import com.chikere.futureai.responsibility.professional.guidance.GuidanceVocabulary;
import com.chikere.futureai.responsibility.professional.policy.ResponsibilityCategory;
import com.chikere.futureai.trace.AssessmentTrace;
import com.chikere.futureai.trace.NarrativeRuleChecks;
import com.chikere.futureai.trace.TraceStage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The {@value ProfessionalPaidNarrativeVersions#V2} validation contract. The deterministic findings are final, so the
 * narrative must keep every identifier in order and remove, reclassify, rerank or contradict nothing. It may add
 * bounded practical interpretation (illustrative uses, human ownership, a first experiment, role-specific skill
 * manifestations, an adaptation strategy); those additions are held to list sizes and lengths, must not repeat
 * generic or identical copy across items, and must keep high-stakes and safety-critical examples on the safe side of
 * preparation, documentation, checking and review. The language rules carry over the lessons of the role-level
 * narrative v7 and paid v1; they are kept here, versioned with this contract.
 *
 * <p>Quoted responsibility texts are the customer's own words and are removed before the language rules run, so a
 * responsibility such as "Follow safety regulations" never trips a rule by being quoted.</p>
 */
public final class ProfessionalPaidNarrativeValidator {

    public static final String PRESENT = "PAID_NARRATIVE_PRESENT";
    public static final String WELL_FORMED = "PAID_WELL_FORMED_STRUCTURED_OUTPUT";
    public static final String IDS = "PAID_IDS_MATCH_DETERMINISTIC_ORDER";
    public static final String LIST_SIZES = "PAID_LIST_SIZES_AND_LENGTHS";
    public static final String NUMERIC = "NO_NUMERIC_SCORES_OR_PERCENTAGES";
    public static final String BAND_CONTRADICTION = "NO_SCORE_OR_BAND_CONTRADICTIONS";
    public static final String JOB_LOSS = "NO_JOB_LOSS_OR_SECURITY_INFERENCE";
    public static final String CAPABILITY = "NO_CATEGORICAL_AI_CAPABILITY_CLAIMS";
    public static final String TASK_AUTOMATION = "NO_UNSUPPORTED_TASK_AUTOMATION_CLAIMS";
    public static final String FUTURE = "NO_UNSUPPORTED_FUTURE_PREDICTIONS";
    public static final String OCCUPATION = "NO_OCCUPATION_WIDE_CLAIMS_OR_STEREOTYPES";
    public static final String RECLASSIFICATION = "NO_RESPONSIBILITY_RECLASSIFICATION";
    public static final String RANKING = "NO_RANKING_CHANGES";
    public static final String NEW_RECOMMENDATIONS = "NO_NEW_RECOMMENDATIONS";
    public static final String DELEGATION = "NO_UNSAFE_DELEGATION";
    public static final String HIGH_STAKES = "NO_HIGH_STAKES_DELEGATION_IN_EXAMPLES";
    public static final String PROCEDURE = "NO_UNSAFE_PROCEDURE_DEVIATION";
    public static final String CONFIDENTIAL = "NO_UNAPPROVED_CONFIDENTIAL_DATA_HANDLING";
    public static final String REGULATION = "NO_UNSUPPORTED_REGULATORY_STATEMENTS";
    public static final String VENDORS = "NO_NAMED_AI_VENDORS_OR_TOOLS";
    public static final String SYSTEMS = "NO_INVENTED_EMPLOYER_SYSTEMS";
    public static final String INTERNALS = "NO_INTERNAL_IDENTIFIERS_OR_CALIBRATION_LANGUAGE";
    public static final String BAND_WORDS = "NO_ENUM_STYLE_BAND_WORDS";
    public static final String SPELLING = "BRITISH_ENGLISH_SPELLING";
    public static final String SECOND_PERSON = "SECOND_PERSON";
    public static final String TONE = "RESTRAINED_TONE";
    public static final String GENERIC = "NO_GENERIC_OR_REPEATED_PRACTICAL_CONTENT";

    static final List<String> RULES = List.of(PRESENT, WELL_FORMED, IDS, LIST_SIZES, NUMERIC, BAND_CONTRADICTION,
            JOB_LOSS, CAPABILITY, TASK_AUTOMATION, FUTURE, OCCUPATION, RECLASSIFICATION, RANKING, NEW_RECOMMENDATIONS,
            DELEGATION, HIGH_STAKES, PROCEDURE, CONFIDENTIAL, REGULATION, VENDORS, SYSTEMS, INTERNALS, BAND_WORDS,
            SPELLING, SECOND_PERSON, TONE, GENERIC);

    /** Inclusive bounds on the practical lists; each matches the prompt's FIELDS section. */
    static final int MIN_PRACTICAL_USES = 2;
    static final int MAX_PRACTICAL_USES = 4;
    static final int MIN_HUMAN_OWNERSHIP = 1;
    static final int MAX_HUMAN_OWNERSHIP = 3;
    static final int MIN_IN_YOUR_WORK = 2;
    static final int MAX_IN_YOUR_WORK = 3;
    static final int MIN_STRATEGY_ITEMS = 1;
    static final int MAX_STRATEGY_ITEMS = 3;
    /** Character bounds: long enough to be specific, short enough to stay a concise report line. */
    static final int MIN_ITEM = 12;
    static final int MAX_ITEM = 240;
    static final int MAX_EXPERIMENT = 420;
    static final int MAX_EXPLANATION = 650;
    static final int MAX_SUMMARY = 2400;
    static final int MAX_SECTION = 1200;

    /** Rules the single repair attempt may correct. A missing narrative is a provider failure, not prose to fix. */
    public static final Set<String> REPAIRABLE = Set.copyOf(RULES.subList(1, RULES.size()));

    private static final Pattern NUMERIC_CLAIM = Pattern.compile(
            "(?i)\\b\\d+(?:\\.\\d+)?\\s*(?:%|per\\s?cent\\b|/\\s*10\\b|out\\s+of\\s+(?:10|ten)\\b|points?\\b)"
                    + "|\\b\\d+\\.\\d+\\b|\\bscor(?:e|ed|es|ing)\\s+(?:of\\s+)?\\d");

    private static final List<Pattern> JOB_LOSS_CLAIMS = patterns(
            "\\b(?:risk|chance|likelihood|probability|odds|danger)\\s+(?:of|that)\\s+(?:you\\s+)?(?:will\\s+)?(?:losing"
                    + "|lose|job\\s+loss|redundanc\\w*|being\\s+(?:replaced|made\\s+redundant|automated)|replacement"
                    + "|automation)\\b",
            "\\b(?:lose|losing)\\s+(?:your|their|the)\\s+(?:job|role|position|employment)\\b",
            "\\bjob\\s+(?:security|safety|stability|loss)\\b",
            "\\b(?:your|the|this)\\s+(?:job|role|position|work|career)\\s+(?:is|are|will\\s+be|remains?|seems?|appears?)\\s+"
                    + "(?:\\w+\\s+)?(?:safe|secure|protected|at\\s+risk|threatened|in\\s+danger)\\b",
            "\\bat\\s+(?:a\\s+)?(?:low|high|little|no|some|greater|lower)?\\s*risk\\s+of\\s+(?:automation|redundancy"
                    + "|replacement|being\\s+replaced|job\\s+loss)\\b",
            "\\bautomation\\s+risk\\b", "\\breplacement\\s+(?:risk|probability)\\b",
            // Your earnings, not a business's: practical finance examples talk about company earnings.
            "\\b(?:salary|salaries|pay\\s+rise|your\\s+earnings|earning\\s+potential|promotion\\s+prospects)\\b");

    private static final List<Pattern> CAPABILITY_CLAIMS = patterns(
            "\\b(?:AI|artificial\\s+intelligence|machines?|technology|automation|computers?|algorithms?)\\s+"
                    + "(?:(?:currently|still|simply|really|yet|today|ever|fully|easily|truly)\\s+)?"
                    + "(?:cannot|can\\s+not|can't|is\\s+unable\\s+to|are\\s+unable\\s+to|will\\s+never|could\\s+never"
                    + "|can\\s+never|is\\s+incapable\\s+of|are\\s+incapable\\s+of|lacks?\\s+the\\s+(?:ability|capacity))\\b",
            "\\b(?:cannot|can\\s+not|can't)\\s+(?:\\w+\\s+){0,2}?(?:be\\s+)?(?:replicated|replaced|automated|reproduced"
                    + "|substituted|imitated|done\\s+by\\s+(?:AI|machines?))\\b",
            "\\b(?:only|uniquely)\\s+(?:a\\s+)?(?:humans?|people|a\\s+person|human\\s+beings)\\s+(?:can|could)\\b",
            "\\bimpossible\\s+(?:for\\s+(?:AI|machines?|technology)|to\\s+(?:automate|replicate|replace))\\b",
            "\\bbeyond\\s+(?:the\\s+)?(?:reach|capabilit\\w*|abilit\\w*)\\s+of\\s+(?:AI|machines?|technology)\\b",
            "\\birreplaceable\\b", "\\bAI[-\\s]proof\\b",
            "\\bAI\\s+(?:can|could|will|would|may|might)\\s+(?:\\w+\\s+)?replace\\b",
            "\\breplace(?:s|d)?\\s+(?:you|people|humans?|workers|staff|professionals)\\b");

    private static final List<Pattern> TASK_AUTOMATION_CLAIMS = patterns(
            "\\b(?:can|could|will|would)\\s+(?:\\w+\\s+){0,2}?be\\s+(?:\\w+\\s+)?(?:automated|handled|performed|done"
                    + "|replaced|taken\\s+over|carried\\s+out|completed|managed)\\s+(?:\\w+\\s+){0,3}?(?:by|with|using)\\s+"
                    + "(?:AI|artificial\\s+intelligence|machines?|automation|software|algorithms?|automated)\\b",
            "\\b(?:can|could|will|would)\\s+(?:\\w+\\s+){0,2}?be\\s+(?:\\w+ly\\s+)?automated\\b",
            "\\b(?:is|are|seems?|appears?)\\s+(?:\\w+\\s+)?automatable\\b",
            "\\bautomatable\\s+tasks?\\b", "\\btasks?\\s+to\\s+replace\\b",
            "(?<!what\\s)\\bAI(?:\\s+(?:systems?|tools?|models?|software))?\\s+(?:can|could|will|would)\\s+(?:\\w+\\s+)?"
                    + "(?:perform|handle|do|take\\s+over|complete|run|carry\\s+out|automate|manage)\\b",
            "\\bwork\\s+(?:that\\s+)?AI\\s+can\\s+take\\s+over\\b");

    private static final List<Pattern> FUTURE_CLAIMS = patterns(
            "\\b(?:likely|expected|set|bound|poised|going|predicted|projected|forecast|anticipated)\\s+to\\s+"
                    + "(?:\\w+\\s+)?(?:change|evolve|shift|transform|become|grow|decline|shrink|increase|disappear"
                    + "|be\\s+(?:transformed|automated|reshaped|changed|disrupted|replaced))\\b",
            "\\bwill\\s+(?:\\w+\\s+)?(?:change|evolve|shift|transform|become|grow|decline|shrink|disappear"
                    + "|be\\s+(?:transformed|reshaped|changed|disrupted|replaced|automated|eliminated))\\b",
            "\\bwill\\s+increasingly\\b", "\\bwill\\s+remain\\b", "\\bwill\\s+always\\b",
            "\\bwill\\s+continue\\s+to\\s+(?:be|require|need|rely|depend|play|matter|demand)\\b",
            "\\bunlikely\\s+to\\s+(?:be\\s+)?(?:replaced|automated|eliminated|disappear|change|affected|disrupted)\\b",
            "\\bsafe\\s+from\\b", "\\bfuture[-\\s]?proof\\w*", "\\bdoomed\\b", "\\bobsolete\\b",
            "\\b(?:protected|shielded|immune|insulated|secure)\\s+(?:from|against)\\s+(?:AI|artificial\\s+intelligence"
                    + "|automation|replacement|technolog\\w*)\\b",
            "\\bAI\\s+will\\s+(?:replace|take\\s+over|eliminate|transform)\\b", "\\bin\\s+the\\s+(?:coming|next)\\s+"
                    + "(?:years?|decade)\\b", "\\bby\\s+20\\d\\d\\b");

    private static final List<Pattern> STEREOTYPES = patterns(
            "\\b(?:people|professionals|workers|staff|those|everyone)\\s+in\\s+(?:this|your|the\\s+same)\\s+"
                    + "(?:role|profession|occupation|field|line\\s+of\\s+work|industry)\\s+(?:are|tend|usually|typically|often)\\b",
            "\\b(?:most|all|many|typical)\\s+(?:people|professionals|workers)\\s+(?:in|like)\\s+(?:you|your)\\b",
            "\\b(?:as\\s+a\\s+rule|by\\s+nature)\\b");

    /** Ranking talk the narrative could use to reorder fixed priorities. */
    private static final List<Pattern> RANKING_CLAIMS = patterns(
            "\\bre-?(?:rank|order|prioriti[sz])\\w*\\b",
            "\\b(?:more|less)\\s+(?:important|promising|suitable|valuable)\\s+than\\b",
            "\\b(?:should|could)\\s+come\\s+(?:first|before|ahead)\\b",
            "\\b(?:better|best)\\s+(?:to\\s+)?start\\s+with\\b(?!\\s+the\\s+pilot)",
            "\\binstead\\s+of\\s+(?:the|this|that)\\s+(?:opportunity|pilot|priority|action|skill)\\b",
            "\\b(?:lowest|least)\\s+(?:priority|important)\\b");

    /** New recommendations the deterministic guidance never makes. */
    private static final List<Pattern> NEW_RECOMMENDATION_CLAIMS = patterns(
            "\\b(?:courses?|certificat\\w*|qualifications?|bootcamps?|diplomas?|webinars?|tutorials?|MOOCs?)\\b",
            "\\b(?:enrol|enroll|sign\\s+up|subscribe|purchase|buy|download)\\w*\\b",
            // Installing software or a tool. Not the noun "installation" or installing physical work, which
            // describes the customer's own responsibilities (an electrician's installation work).
            "\\binstall(?:s|ed|ing)?\\s+(?:(?:a|an|the|some|new|any|this|that|your)\\s+)?(?:[\\w-]+\\s+){0,2}?"
            + "(?:apps?|applications?|software|tools?|plug-?ins?|extensions?|add-?ons?|programs?|assistants?|AI)\\b",
            "\\b(?:hire|recruit)\\s+(?:a|an|someone)\\b",
            "\\b(?:change|switch|leave)\\s+(?:your\\s+)?(?:career|careers|job|jobs|profession|role)\\b",
            "\\bretrain\\w*\\b", "\\blearn\\s+(?:AI|to\\s+code|coding|programming|prompt\\s+engineering)\\b",
            "\\bprompt\\s+engineering\\b");

    /** Handing judgement, decisions or responsibility to AI, or removing human review. */
    private static final List<Pattern> DELEGATION_CLAIMS = patterns(
            "\\b(?:let|allow|have)\\s+(?:the\\s+)?AI\\s+(?:to\\s+)?(?:decide|make|take|approve|sign|choose|judge|diagnose)\\w*\\b",
            "\\b(?:leave|hand|pass|delegate|offload|outsource|hand\\s+over|hand\\s+off)\\s+(?:\\w+\\s+){0,4}?to\\s+"
                    + "(?:the\\s+)?(?:AI|an?\\s+AI|AI\\s+tools?|automation)\\b",
            "\\b(?:rely|depend)\\s+(?:entirely|solely|fully|wholly|completely)\\s+on\\s+(?:the\\s+)?AI\\b",
            "\\bwithout\\s+(?:any\\s+)?(?:human\\s+)?(?:review|checking|checks|oversight|sign-?off)\\b",
            // "How much review effort it needed" is a v2 success measure, not removing review.
            "\\b(?:skip|drop|remove|reduce|relax)\\w*\\s+(?:\\w+\\s+){0,2}?(?:human\\s+)?(?:review|checking|checks|oversight)\\b"
                    + "(?!\\s+(?:effort|time|burden|workload))",
            "\\bAI\\s+(?:should|can|could|will)\\s+(?:\\w+\\s+)?(?:make|take)\\s+(?:the\\s+)?(?:\\w+\\s+)?decisions?\\b",
            "\\btrust\\s+(?:the\\s+)?AI(?:'s)?\\s+(?:output|answer|judgement|decision)s?\\b",
            "\\bAI\\s+(?:should|can|could|will|may|might|would)\\s+(?:\\w+\\s+)?(?:diagnose|prescribe|approve|sign\\s+off"
                    + "|authori[sz]e|decide)\\w*\\b");

    /**
     * In an AI-use example, final authority: approving, signing off, deciding or escalating. Acceptable only when the
     * same example keeps that authority with you ("ready for your sign-off", "so you can decide whether to escalate").
     */
    private static final Pattern FINAL_AUTHORITY = Pattern.compile("(?i)\\b(?:approv(?:e|es|ing)\\b|sign(?:s|ing|ed)?"
            + "[-\\s]off|authoris\\w*|authoriz\\w*|final\\s+(?:decisions?|judgements?|conclusions?|approvals?|say)"
            + "|decid\\w*|determin\\w*\\s+whether|escalat\\w*)");
    private static final Pattern HUMAN_OWNERSHIP = Pattern.compile("(?i)\\b(?:for\\s+(?:you|your)\\b|you\\s+(?:then\\s+"
            + "|still\\s+|can\\s+|alone\\s+)?(?:decide|confirm|check|review|approve|sign|own|make|judge|choose|assess)"
            + "|your\\s+(?:own\\s+)?(?:review|decision|judgement|approval|sign-?off|check|assessment|call)"
            + "|before\\s+you\\b|so\\s+(?:that\\s+)?you\\b|ready\\s+for\\s+you|by\\s+you\\b|yours\\b)");
    /**
     * In a safety-critical example, work AI must never be described doing: diagnosis, treatment, prescribing, doses,
     * triage or direct assessment of a person. Generic vocabulary for safety-critical work, not an occupation list.
     */
    private static final Pattern SAFETY_CRITICAL_ACTS = Pattern.compile("(?i)\\b(?:diagnos\\w*|prescrib\\w*|dosages?"
            + "|doses?|dosing|triag\\w*|treatment\\s+(?:decisions?|plans?|options?|choices?)|clinical\\s+(?:decisions?"
            + "|judgements?)|assess\\w*\\s+(?:the\\s+|a\\s+)?(?:patients?|persons?|people|clients?|child(?:ren)?|residents?"
            + "|service\\s+users?|casualt\\w*))\\b");

    /** Putting personal or confidential information into a tool, unless the sentence keeps it to approved tools. */
    private static final Pattern CONFIDENTIAL_HANDLING = Pattern.compile("(?i)\\b(?:paste|upload|enter|put|copy|share"
            + "|send|feed|input|load)\\w*\\s+(?:\\w+\\s+){0,4}?(?:patient|client|customer|personal|confidential|sensitive"
            + "|identifiable|employee|staff|payroll|financial|commercial|private)\\s+(?:\\w+\\s+)?(?:data|information"
            + "|records?|details|notes|figures|files|documents)\\b(?:\\s+\\w+){0,3}?\\s+(?:into|to|with)\\s+(?:[\\w-]+\\s+){0,3}?"
            + "(?:AI|tools?|assistants?|chatbots?|apps?|systems?|models?|platforms?|services?|software)\\b");
    private static final Pattern APPROVED = Pattern.compile("(?i)\\bapproved\\b");

    /** Statements of what law, regulation or a professional body requires, which the stored analysis never supplies. */
    private static final List<Pattern> REGULATORY_CLAIMS = patterns(
            "\\b(?:the\\s+)?(?:law|legislation|regulations?|regulators?|regulatory\\s+(?:bod(?:y|ies)|rules?|framework)"
                    + "|professional\\s+bod(?:y|ies)|codes?\\s+of\\s+(?:conduct|practice))\\s+(?:requires?|mandates?|says"
                    + "|states|prohibits?|forbids?|bans?|demands?|allows?|permits?)\\b",
            "\\b(?:required|mandated|prohibited|forbidden|permitted)\\s+(?:by|under)\\s+(?:the\\s+)?(?:law|legislation"
                    + "|regulations?|(?:your\\s+|the\\s+)?regulator)\\b",
            "\\b(?:legally|by\\s+law)\\s+(?:required|obliged|bound)\\b");
    /** Named regulations and bodies: allowed only when the person named them in their own work description. */
    private static final Pattern NAMED_REGULATION = Pattern.compile("\\b(?:GDPR|HIPAA|Data\\s+Protection\\s+Act"
            + "|Caldicott|NMC|GMC|HCPC|CQC|CIMA|ACCA|ICAEW|FCA|PRA|SRA|IFRS|GAAP|SOX|Sarbanes-Oxley|NICE|Ofsted|HSE)\\b");

    /** An employer system the person never mentioned: "your trust's records system", "the ERP". */
    private static final Pattern EMPLOYER_SYSTEM = Pattern.compile("(?i)\\b(?:your|the)\\s+(?:organisation|organization"
            + "|employer|company|firm|trust|hospital|practice|team|department|ward|school|business)(?:'s|’s)\\s+"
            + "(?!approved\\b)(?:[\\w-]+\\s+){0,2}?(?:systems?|software|platforms?|databases?|portals?|ledgers?|intranet"
            + "|apps?)\\b");
    private static final Pattern SYSTEM_ACRONYM = Pattern.compile("\\b(?:ERP|EHR|EPR|EMR|CRM|HRIS|PAS|MIS)s?\\b");

    private static final Pattern DEVIATION = Pattern.compile("(?i)\\b(?:ignor\\w*|bypass\\w*|skip\\w*|loosen\\w*"
            + "|relax\\w*|deviat\\w*|depart\\w*\\s+from|work(?:ing)?\\s+around|circumvent\\w*|overrid\\w*"
            + "|disregard\\w*|shortcut\\w*|cut(?:ting)?\\s+corners|abandon\\w*|bend(?:ing)?|waiv\\w*|set(?:ting)?\\s+aside)\\b");
    private static final Pattern REGULATED = Pattern.compile("(?i)\\b(?:procedures?|protocols?|checks?|checklists?"
            + "|polic(?:y|ies)|guidelines?|regulations?|regulatory\\s+requirements?|legal\\s+(?:obligations?"
            + "|requirements?)|professional\\s+(?:standards?|obligations?|duties)|security\\s+controls?|controls?"
            + "|safeguards?|confidentiality|privacy|data\\s+protection|consent|compliance|approvals?)\\b");
    private static final Pattern SAFETY_NEGATION = Pattern.compile("(?i)\\b(?:never|not|no|without|avoid\\w*"
            + "|rather\\s+than|instead\\s+of)\\b|n't\\b");

    private static final Pattern VENDOR = Pattern.compile("(?i)\\b(?:ChatGPT|GPT-?\\d\\w*|OpenAI|Copilot|Gemini|Bard"
            + "|Claude|Anthropic|Llama|Mistral|Perplexity|Grammarly|Jasper|Midjourney|DALL-?E|Notion\\s+AI|Otter"
            + "|Microsoft|Google|Amazon|AWS|Azure|IBM\\s+Watson|Salesforce|Einstein)\\b");
    /** Proper product names, case-sensitive so "care teams" or "sage advice" never match. */
    private static final Pattern PRODUCT = Pattern.compile("\\b(?:Excel|Power\\s?BI|Tableau|SAP|Oracle|Xero|Sage"
            + "|QuickBooks|Epic|Cerner|SharePoint|Slack|Outlook|Word|PowerPoint|Zoom)\\b");

    private static final List<Pattern> INTERNAL_LANGUAGE = List.of(
            Pattern.compile("\\b[A-Z][A-Z0-9]*_[A-Z0-9_]+\\b"),
            Pattern.compile("(?i)\\b(?:opportunity|human-value|skill|action)-\\d+-[a-z0-9-]+\\b"),
            // Not "distribution costs" or a "materiality threshold": ordinary terms in practical examples.
            Pattern.compile("(?i)\\b(?:probabilit(?:y|ies)|(?:probability|score|judge?ment)\\s+distributions?"
                    + "|provider\\s+confidence|confidence\\s+(?:score|level|interval)|calibrat\\w*"
                    + "|(?<!materiality\\s)(?<!reporting\\s)(?<!agreed\\s)threshold\\w*|described\\s+clearly|close\\s+call|Jev|TypeSafe"
                    + "|language\\s+model|LLM|rule\\s+(?:id|identifier))\\b"));

    private static final Pattern ENUM_BAND_WORD = Pattern.compile("\\b(?:LOW|MODERATE|HIGH)\\b");
    private static final Pattern AMERICAN_SPELLING = Pattern.compile("(?i)\\b(?:judgments?|prioritiz\\w*"
            + "|personaliz\\w*|organiz\\w*|emphasiz\\w*|analyz\\w*|recogniz\\w*|behaviors?|optimiz\\w*|specializ\\w*"
            + "|utiliz\\w*|minimiz\\w*|maximiz\\w*|standardiz\\w*|summariz\\w*|familiariz\\w*|center(?:s|ed)?"
            + "|color\\w*|favor\\w*|labor)\\b");
    private static final Pattern SECOND_PERSON_WORDS = Pattern.compile("(?i)\\b(?:you|your|yours|yourself)\\b");
    /**
     * Writing about the assessed person rather than to them. Not "the customer" or "the client", who in customer-facing
     * and professional work are usually the people you serve ("your understanding of the customer's situation"). A
     * hyphenated compound ("the worker-led steps") describes the work, so the noun must not continue with a hyphen.
     */
    private static final Pattern THIRD_PERSON = Pattern.compile("(?i)\\b(?:the\\s+(?:user|respondent"
            + "|individual|person\\s+who\\s+completed|employee|worker)(?![\\w-])"
            + "|this\\s+(?:person|individual|professional)\\b|he\\s+or\\s+she\\b)");
    /**
     * A clause telling a named third person what to do: "A customer-service adviser must...". Not "they", which a
     * regex cannot resolve and usually refers to things ("AI drafts help, but they should still be checked by you").
     */
    private static final String THIRD_PERSON_SUBJECT = "(?:(?:a|an|the)\\s+(?:[\\w-]+\\s+){0,3}?(?:worker|employee"
            + "|professional|practitioner|%s))";
    private static final String THIRD_PERSON_INSTRUCTION = "(?i)(?:^\\s*|[,;:]\\s*(?:(?:and|but|so|while|yet)\\s+)?)"
            + "%s\\s+(?:should|must|ought\\s+to|needs?\\s+to|has\\s+to|have\\s+to)\\b";
    private static final Pattern HYPE = Pattern.compile("(?i)!|\\b(?:amazing|exciting|revolutionar\\w*|game[-\\s]?chang\\w*"
            + "|dramatic(?:ally)?|massive(?:ly)?|huge(?:ly)?|incredibl[ey]|unprecedented|skyrocket\\w*|supercharg\\w*"
            + "|transformative|disrupt\\w*|alarming|terrif\\w*|guarantee\\w*|definitely|certainly|undoubtedly"
            + "|your\\s+job\\s+is\\s+at\\s+risk)\\b");

    private static final Pattern SENTENCE_BREAK = Pattern.compile("(?<=[.!?])\\s+");
    private static final Pattern CLAUSE_BREAK = Pattern.compile("(?i);|\\bbut\\b|\\bhowever\\b");
    private static final Pattern DISCLAIMER = Pattern.compile("(?i)"
            + "\\b(?:does|do|did|is|are|was|were|should|can|could|would|will)\\s*(?:not|n't)\\s+(?:\\w+\\s+){0,3}?"
            + "(?:predict|show|mean|imply|indicate|suggest|claim|say|tell|assess|measure|forecast|establish|prove"
            + "|determine|guarantee|describe|reflect|evaluate|judge|consider)\\w*"
            + "|\\bnot\\s+(?:a|an)\\s+(?:prediction|probability|forecast|guarantee|measure|assessment|statement"
            + "|judgement|verdict|claim)\\b"
            + "|\\bwithout\\s+(?:predicting|implying|suggesting|claiming)\\b"
            + "|\\bno\\s+(?:prediction|claim|forecast|guarantee)\\b");

    private final AssessmentTrace trace;

    public ProfessionalPaidNarrativeValidator() {
        this(AssessmentTrace.disabled());
    }

    public ProfessionalPaidNarrativeValidator(AssessmentTrace trace) {
        this.trace = Objects.requireNonNull(trace, "trace must not be null");
    }

    /** Returns the narrative unchanged if every rule passes; otherwise throws the first rule's violation. */
    public ProfessionalPaidNarrative validate(ProfessionalPaidNarrative narrative, ProfessionalPaidNarrativeContext context) {
        Objects.requireNonNull(context, "context must not be null");
        NarrativeRuleChecks checks = NarrativeRuleChecks.start(trace, JourneyType.PROFESSIONAL, RULES,
                TraceStage.PAID_NARRATIVE_VALIDATION);
        checks.check(PRESENT, narrative == null, () -> null, "the provider returned no paid narrative");
        checks.check(WELL_FORMED, malformed(narrative).isPresent(), () -> malformed(narrative).orElse(null),
                "a required paid-narrative field is missing or empty");
        checks.check(IDS, idMismatch(narrative, context).isPresent(), () -> idMismatch(narrative, context).orElse(null),
                "every deterministic identifier must appear exactly once, in the deterministic order, and nothing else");
        Optional<String> sizes = sizeOrLength(narrative);
        checks.check(LIST_SIZES, sizes.isPresent(), sizes::orElseThrow,
                "each practical list must have the stated number of items and every text a concise, specific length");

        List<String> prose = prose(narrative, context);
        claim(checks, NUMERIC, prose, List.of(NUMERIC_CLAIM), false,
                "the paid narrative must not state scores, percentages or other numbers");
        Optional<String> contradiction = bandContradiction(prose, context);
        checks.check(BAND_CONTRADICTION, contradiction.isPresent(), contradiction::orElseThrow,
                "a measure must only be described with its stored band");
        claim(checks, JOB_LOSS, prose, JOB_LOSS_CLAIMS, true,
                "the paid narrative must not infer job loss, job security, salaries or career outcomes");
        claim(checks, CAPABILITY, prose, CAPABILITY_CLAIMS, true,
                "the paid narrative must not make categorical claims about what AI can or cannot do or replace");
        claim(checks, TASK_AUTOMATION, prose, TASK_AUTOMATION_CLAIMS, true,
                "the paid narrative must describe assistance, not tasks AI can perform, automate or take over");
        claim(checks, FUTURE, prose, FUTURE_CLAIMS, true,
                "the paid narrative must not predict the future of the work, role or profession");
        List<Pattern> occupation = new ArrayList<>(STEREOTYPES);
        occupationWideClaim(context.profession()).ifPresent(occupation::add);
        claim(checks, OCCUPATION, prose, occupation, false,
                "the paid narrative must describe the work you described, not everyone in the occupation");
        Optional<String> reclassified = reclassification(narrative, context);
        checks.check(RECLASSIFICATION, reclassified.isPresent(), reclassified::orElseThrow,
                "an explanation must not give a responsibility a different category from the stored one");
        claim(checks, RANKING, prose, RANKING_CLAIMS, false,
                "the paid narrative must not reorder or reprioritise the deterministic findings");
        Optional<String> invented = newRecommendation(prose, context);
        checks.check(NEW_RECOMMENDATIONS, invented.isPresent(), invented::orElseThrow,
                "the paid narrative must not add courses, tools, career moves or skills that were not supplied");
        Optional<String> delegation = unnegated(prose, DELEGATION_CLAIMS);
        checks.check(DELEGATION, delegation.isPresent(), delegation::orElseThrow,
                "the paid narrative must keep human review, judgement and decisions with you");
        Optional<String> highStakes = highStakesDelegation(narrative, context);
        checks.check(HIGH_STAKES, highStakes.isPresent(), highStakes::orElseThrow,
                "an AI-use example must keep approval, sign-off, decisions and escalation with you, and safety-critical "
                        + "examples must never describe diagnosis, treatment, prescribing, doses, triage or direct assessment");
        Optional<String> deviation = procedureDeviation(prose);
        checks.check(PROCEDURE, deviation.isPresent(), deviation::orElseThrow,
                "the paid narrative must never suggest setting aside a procedure, policy or obligation");
        Optional<String> confidential = confidentialHandling(prose);
        checks.check(CONFIDENTIAL, confidential.isPresent(), confidential::orElseThrow,
                "the paid narrative must never suggest putting personal or confidential information into a tool that "
                        + "is not approved for it");
        Optional<String> regulation = regulatoryClaim(prose, context);
        checks.check(REGULATION, regulation.isPresent(), regulation::orElseThrow,
                "the paid narrative must not state what law, regulation or a professional body requires");
        Optional<String> vendor = unsupplied(prose, List.of(VENDOR, PRODUCT), context);
        checks.check(VENDORS, vendor.isPresent(), vendor::orElseThrow,
                "the paid narrative must not name AI vendors, products or tools");
        Optional<String> system = unsupplied(prose, List.of(EMPLOYER_SYSTEM, SYSTEM_ACRONYM), context);
        checks.check(SYSTEMS, system.isPresent(), system::orElseThrow,
                "the paid narrative must not assume employer systems, software or data sources you did not mention");
        claim(checks, INTERNALS, prose, INTERNAL_LANGUAGE, false,
                "the paid narrative must not expose identifiers, probabilities or calibration language");
        claim(checks, BAND_WORDS, prose, List.of(ENUM_BAND_WORD), false,
                "band words must be written in ordinary lower case inside sentences");
        claim(checks, SPELLING, prose, List.of(AMERICAN_SPELLING), false, "the paid narrative must use British English");
        Optional<String> person = secondPerson(narrative, prose, context.profession());
        checks.check(SECOND_PERSON, person.isPresent(), person::orElseThrow,
                "the paid narrative must address you directly, in the second person");
        claim(checks, TONE, prose, List.of(HYPE), false, "the paid narrative must keep a calm, restrained tone");
        Optional<String> generic = genericOrRepeated(narrative);
        checks.check(GENERIC, generic.isPresent(), generic::orElseThrow,
                "practical content must be specific to each responsibility: no repeated items and no generic category copy");
        checks.accepted();
        return narrative;
    }

    private static Optional<String> malformed(ProfessionalPaidNarrative narrative) {
        if (narrative == null) {
            return Optional.empty();
        }
        List<String> missing = new ArrayList<>();
        text("executiveSummary", narrative.executiveSummary(), missing);
        text("scoresTogether", narrative.scoresTogether(), missing);
        text("responsibilityPattern", narrative.responsibilityPattern(), missing);
        text("planIntroduction", narrative.planIntroduction(), missing);
        text("closingPerspective", narrative.closingPerspective(), missing);
        if (narrative.opportunityInsights() == null) {
            missing.add("opportunityInsights");
        }
        else {
            for (int index = 0; index < narrative.opportunityInsights().size(); index++) {
                OpportunityInsight item = narrative.opportunityInsights().get(index);
                String name = "opportunityInsights[" + index + "]";
                if (item == null) {
                    missing.add(name);
                    continue;
                }
                text(name + ".id", item.id(), missing);
                text(name + ".explanation", item.explanation(), missing);
                strings(name + ".practicalAiUses", item.practicalAiUses(), missing);
                strings(name + ".humanOwnership", item.humanOwnership(), missing);
                text(name + ".firstExperiment", item.firstExperiment(), missing);
            }
        }
        if (narrative.humanValueInsights() == null) {
            missing.add("humanValueInsights");
        }
        else {
            for (int index = 0; index < narrative.humanValueInsights().size(); index++) {
                HumanValueInsight item = narrative.humanValueInsights().get(index);
                String name = "humanValueInsights[" + index + "]";
                if (item == null) {
                    missing.add(name);
                    continue;
                }
                text(name + ".id", item.id(), missing);
                text(name + ".explanation", item.explanation(), missing);
                text(name + ".strengthen", item.strengthen(), missing);
            }
        }
        if (narrative.skillInsights() == null) {
            missing.add("skillInsights");
        }
        else {
            for (int index = 0; index < narrative.skillInsights().size(); index++) {
                SkillInsight item = narrative.skillInsights().get(index);
                String name = "skillInsights[" + index + "]";
                if (item == null) {
                    missing.add(name);
                    continue;
                }
                text(name + ".id", item.id(), missing);
                text(name + ".explanation", item.explanation(), missing);
                strings(name + ".inYourWorkThisMeans", item.inYourWorkThisMeans(), missing);
            }
        }
        if (narrative.planActionNotes() == null) {
            missing.add("planActionNotes");
        }
        else {
            for (int index = 0; index < narrative.planActionNotes().size(); index++) {
                ItemExplanation item = narrative.planActionNotes().get(index);
                if (item == null || item.id() == null || item.id().isBlank() || item.explanation() == null
                        || item.explanation().isBlank()) {
                    missing.add("planActionNotes[" + index + "]");
                }
            }
        }
        AdaptationStrategy strategy = narrative.adaptationStrategy();
        if (strategy == null) {
            missing.add("adaptationStrategy");
        }
        else {
            strings("adaptationStrategy.protect", strategy.protect(), missing);
            strings("adaptationStrategy.learn", strategy.learn(), missing);
            strings("adaptationStrategy.experiment", strategy.experiment(), missing);
            strings("adaptationStrategy.strengthen", strategy.strengthen(), missing);
            strings("adaptationStrategy.monitor", strategy.monitor(), missing);
        }
        return missing.isEmpty() ? Optional.empty() : Optional.of(String.join(", ", missing));
    }

    private static void text(String name, String value, List<String> missing) {
        if (value == null || value.isBlank()) {
            missing.add(name);
        }
    }

    private static void strings(String name, List<String> values, List<String> missing) {
        if (values == null) {
            missing.add(name);
            return;
        }
        for (int index = 0; index < values.size(); index++) {
            text(name + "[" + index + "]", values.get(index), missing);
        }
    }

    private static Optional<String> idMismatch(ProfessionalPaidNarrative narrative, ProfessionalPaidNarrativeContext context) {
        return Stream.of(
                        compare("opportunityInsights", narrative.opportunityInsights().stream().map(OpportunityInsight::id)
                                .toList(), context.opportunityIds()),
                        compare("humanValueInsights", narrative.humanValueInsights().stream().map(HumanValueInsight::id)
                                .toList(), context.humanValueIds()),
                        compare("skillInsights", narrative.skillInsights().stream().map(SkillInsight::id).toList(),
                                context.skillIds()),
                        compare("planActionNotes", narrative.planActionNotes().stream().map(ItemExplanation::id).toList(),
                                context.planActionIds()))
                .flatMap(Optional::stream).findFirst();
    }

    private static Optional<String> compare(String field, List<String> ids, List<String> expected) {
        List<String> actual = ids.stream().map(String::strip).toList();
        if (actual.equals(expected)) {
            return Optional.empty();
        }
        Set<String> known = new HashSet<>(expected);
        Optional<String> unknown = actual.stream().filter(id -> !known.contains(id)).findFirst();
        if (unknown.isPresent()) {
            return Optional.of(field + ": unknown id " + unknown.get());
        }
        if (new HashSet<>(actual).size() != actual.size()) {
            return Optional.of(field + ": an id appears more than once");
        }
        Optional<String> missing = expected.stream().filter(id -> !actual.contains(id)).findFirst();
        if (missing.isPresent()) {
            return Optional.of(field + ": missing id " + missing.get());
        }
        return Optional.of(field + ": ids are not in the deterministic order " + expected);
    }

    /** Every list within its bounds and every text within its length; the message names the first that is not. */
    private static Optional<String> sizeOrLength(ProfessionalPaidNarrative narrative) {
        List<String> problems = new ArrayList<>();
        length("executiveSummary", narrative.executiveSummary(), MIN_ITEM, MAX_SUMMARY, problems);
        for (String[] field : new String[][] {{"scoresTogether", narrative.scoresTogether()},
                {"responsibilityPattern", narrative.responsibilityPattern()},
                {"planIntroduction", narrative.planIntroduction()},
                {"closingPerspective", narrative.closingPerspective()}}) {
            length(field[0], field[1], MIN_ITEM, MAX_SECTION, problems);
        }
        for (OpportunityInsight item : narrative.opportunityInsights()) {
            length(item.id() + ".explanation", item.explanation(), MIN_ITEM, MAX_EXPLANATION, problems);
            list(item.id() + ".practicalAiUses", item.practicalAiUses(), MIN_PRACTICAL_USES, MAX_PRACTICAL_USES, problems);
            list(item.id() + ".humanOwnership", item.humanOwnership(), MIN_HUMAN_OWNERSHIP, MAX_HUMAN_OWNERSHIP, problems);
            length(item.id() + ".firstExperiment", item.firstExperiment(), MIN_ITEM, MAX_EXPERIMENT, problems);
        }
        for (HumanValueInsight item : narrative.humanValueInsights()) {
            length(item.id() + ".explanation", item.explanation(), MIN_ITEM, MAX_EXPLANATION, problems);
            length(item.id() + ".strengthen", item.strengthen(), MIN_ITEM, MAX_EXPERIMENT, problems);
        }
        for (SkillInsight item : narrative.skillInsights()) {
            length(item.id() + ".explanation", item.explanation(), MIN_ITEM, MAX_EXPLANATION, problems);
            list(item.id() + ".inYourWorkThisMeans", item.inYourWorkThisMeans(), MIN_IN_YOUR_WORK, MAX_IN_YOUR_WORK,
                    problems);
        }
        for (ItemExplanation item : narrative.planActionNotes()) {
            length(item.id(), item.explanation(), MIN_ITEM, MAX_EXPLANATION, problems);
        }
        AdaptationStrategy strategy = narrative.adaptationStrategy();
        list("adaptationStrategy.protect", strategy.protect(), MIN_STRATEGY_ITEMS, MAX_STRATEGY_ITEMS, problems);
        list("adaptationStrategy.learn", strategy.learn(), MIN_STRATEGY_ITEMS, MAX_STRATEGY_ITEMS, problems);
        list("adaptationStrategy.experiment", strategy.experiment(), MIN_STRATEGY_ITEMS, MAX_STRATEGY_ITEMS, problems);
        list("adaptationStrategy.strengthen", strategy.strengthen(), MIN_STRATEGY_ITEMS, MAX_STRATEGY_ITEMS, problems);
        list("adaptationStrategy.monitor", strategy.monitor(), MIN_STRATEGY_ITEMS, MAX_STRATEGY_ITEMS, problems);
        return problems.stream().findFirst();
    }

    private static void list(String name, List<String> values, int minimum, int maximum, List<String> problems) {
        if (values.size() < minimum || values.size() > maximum) {
            problems.add(name + ": " + values.size() + " items, expected " + minimum + "-" + maximum);
        }
        values.forEach(value -> length(name, value, MIN_ITEM, MAX_ITEM, problems));
    }

    private static void length(String name, String value, int minimum, int maximum, List<String> problems) {
        int length = value.strip().length();
        if (length < minimum || length > maximum) {
            problems.add(name + ": " + length + " characters, expected " + minimum + "-" + maximum);
        }
    }

    /** Every prose field, with the customer's own responsibility texts removed. */
    private static List<String> prose(ProfessionalPaidNarrative narrative, ProfessionalPaidNarrativeContext context) {
        List<String> texts = new ArrayList<>(List.of(narrative.executiveSummary(), narrative.scoresTogether(),
                narrative.responsibilityPattern(), narrative.planIntroduction(), narrative.closingPerspective()));
        for (OpportunityInsight item : narrative.opportunityInsights()) {
            texts.add(item.explanation());
            texts.addAll(item.practicalAiUses());
            texts.addAll(item.humanOwnership());
            texts.add(item.firstExperiment());
        }
        narrative.humanValueInsights().forEach(item -> texts.addAll(List.of(item.explanation(), item.strengthen())));
        for (SkillInsight item : narrative.skillInsights()) {
            texts.add(item.explanation());
            texts.addAll(item.inYourWorkThisMeans());
        }
        narrative.planActionNotes().forEach(item -> texts.add(item.explanation()));
        texts.addAll(strategyItems(narrative.adaptationStrategy()));
        return withoutResponsibilityTexts(texts, context);
    }

    private static List<String> strategyItems(AdaptationStrategy strategy) {
        return Stream.of(strategy.protect(), strategy.learn(), strategy.experiment(), strategy.strengthen(),
                strategy.monitor()).flatMap(Collection::stream).toList();
    }

    private static List<String> withoutResponsibilityTexts(List<String> texts, ProfessionalPaidNarrativeContext context) {
        List<String> userTexts = context.responsibilityTexts().stream()
                .sorted((a, b) -> Integer.compare(b.length(), a.length())).toList();
        return texts.stream().map(text -> {
            String cleaned = text;
            for (String user : userTexts) {
                cleaned = cleaned.replaceAll("(?i)" + Pattern.quote(user), "this responsibility");
            }
            return cleaned;
        }).toList();
    }

    /** "Your low AI Exposure" when the stored band is High: a measure described with a band it does not have. */
    private static Optional<String> bandContradiction(List<String> prose, ProfessionalPaidNarrativeContext context) {
        for (var measure : context.bandByMeasure().entrySet()) {
            String name = Pattern.quote(measure.getKey());
            String stored = measure.getValue().toLowerCase(Locale.ROOT);
            Pattern before = Pattern.compile("(?i)\\b(low|moderate|high)\\s+(?:level\\s+of\\s+)?" + name + "\\b");
            Pattern after = Pattern.compile("(?i)\\b" + name + "\\s+(?:score\\s+)?(?:is|was|of|sits|reads|appears|remains)"
                    + "\\s+(?:\\w+\\s+)?(low|moderate|high)\\b");
            for (String text : prose) {
                for (String sentence : SENTENCE_BREAK.split(text.strip())) {
                    for (Pattern pattern : List.of(before, after)) {
                        Matcher matcher = pattern.matcher(sentence);
                        while (matcher.find()) {
                            if (!matcher.group(1).equalsIgnoreCase(stored)) {
                                return Optional.of(sentence.strip());
                            }
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    /**
     * The AI-use examples (practical uses, first experiments and the strategy's experiments) must keep final authority
     * with you; for safety-critical opportunities they must never describe a safety-critical act at all.
     */
    private static Optional<String> highStakesDelegation(ProfessionalPaidNarrative narrative,
            ProfessionalPaidNarrativeContext context) {
        List<String> examples = new ArrayList<>();
        List<String> safetyCritical = new ArrayList<>();
        for (OpportunityInsight item : narrative.opportunityInsights()) {
            List<String> own = new ArrayList<>(item.practicalAiUses());
            own.add(item.firstExperiment());
            examples.addAll(own);
            if (context.safetyCriticalOpportunityIds().contains(item.id().strip())) {
                safetyCritical.addAll(own);
            }
        }
        examples.addAll(narrative.adaptationStrategy().experiment());
        if (!context.safetyCriticalOpportunityIds().isEmpty()) {
            safetyCritical.addAll(narrative.adaptationStrategy().experiment());
        }
        for (String example : withoutResponsibilityTexts(examples, context)) {
            Matcher matcher = FINAL_AUTHORITY.matcher(example);
            while (matcher.find()) {
                boolean negated = SAFETY_NEGATION.matcher(example.substring(0, matcher.start())).find();
                if (!negated && !HUMAN_OWNERSHIP.matcher(example).find()) {
                    return Optional.of(example.strip());
                }
            }
        }
        for (String example : withoutResponsibilityTexts(safetyCritical, context)) {
            Matcher matcher = SAFETY_CRITICAL_ACTS.matcher(example);
            while (matcher.find()) {
                if (!SAFETY_NEGATION.matcher(example.substring(0, matcher.start())).find()) {
                    return Optional.of(example.strip());
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<String> confidentialHandling(List<String> prose) {
        for (String text : prose) {
            for (String sentence : SENTENCE_BREAK.split(text.strip())) {
                Matcher matcher = CONFIDENTIAL_HANDLING.matcher(sentence);
                if (matcher.find() && !APPROVED.matcher(sentence).find()
                        && !SAFETY_NEGATION.matcher(sentence.substring(0, matcher.start())).find()) {
                    return Optional.of(sentence.strip());
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<String> regulatoryClaim(List<String> prose, ProfessionalPaidNarrativeContext context) {
        Optional<String> claim = firstUndisclaimed(prose, REGULATORY_CLAIMS, false);
        return claim.isPresent() ? claim : unsupplied(prose, List.of(NAMED_REGULATION), context);
    }

    /** The first sentence naming something the person did not mention themselves in their work description. */
    private static Optional<String> unsupplied(List<String> prose, List<Pattern> names,
            ProfessionalPaidNarrativeContext context) {
        String own = context.workDescription().toLowerCase(Locale.ROOT);
        for (String text : prose) {
            for (String sentence : SENTENCE_BREAK.split(text.strip())) {
                for (Pattern name : names) {
                    Matcher matcher = name.matcher(sentence);
                    while (matcher.find()) {
                        if (!own.contains(matcher.group().toLowerCase(Locale.ROOT))) {
                            return Optional.of(sentence.strip());
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Copy that reads the same for every responsibility: a practical use, experiment, skill manifestation or
     * strengthening suggestion repeated word for word, an opportunity whose ownership list repeats another's, or the
     * generic category sentence ("preparation, drafting, checking or information handling") offered as an example.
     */
    private static Optional<String> genericOrRepeated(ProfessionalPaidNarrative narrative) {
        Optional<String> repeated = Stream.of(
                        repeated(narrative.opportunityInsights().stream().flatMap(item -> item.practicalAiUses().stream())
                                .toList()),
                        repeated(narrative.opportunityInsights().stream().map(OpportunityInsight::firstExperiment).toList()),
                        repeated(narrative.opportunityInsights().stream().map(OpportunityInsight::explanation).toList()),
                        repeated(narrative.opportunityInsights().stream().map(item -> String.join(" | ",
                                item.humanOwnership())).toList()),
                        repeated(narrative.humanValueInsights().stream().map(HumanValueInsight::strengthen).toList()),
                        repeated(narrative.humanValueInsights().stream().map(HumanValueInsight::explanation).toList()),
                        repeated(narrative.skillInsights().stream().flatMap(item -> item.inYourWorkThisMeans().stream())
                                .toList()))
                .flatMap(Optional::stream).findFirst();
        if (repeated.isPresent()) {
            return repeated;
        }
        for (OpportunityInsight item : narrative.opportunityInsights()) {
            List<String> examples = new ArrayList<>(item.practicalAiUses());
            examples.add(item.firstExperiment());
            for (String example : examples) {
                String flat = normalised(example);
                if (GENERIC_CATEGORY_COPY.stream().anyMatch(flat::contains)) {
                    return Optional.of(example.strip());
                }
            }
        }
        return Optional.empty();
    }

    /** The deterministic category sentences, normalised; as an "example" they add nothing specific. */
    private static final List<String> GENERIC_CATEGORY_COPY = Stream.concat(
                    Stream.of("preparation drafting checking or information handling", "organise information beforehand"),
                    Stream.of(GuidanceVocabulary.OpportunityType.values()).map(type -> normalised(type.guidance())))
            .toList();

    private static Optional<String> repeated(List<String> values) {
        Set<String> seen = new HashSet<>();
        for (String value : values) {
            if (!seen.add(normalised(value))) {
                return Optional.of(value.strip());
            }
        }
        return Optional.empty();
    }

    private static String normalised(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").strip();
    }

    private static Optional<String> reclassification(ProfessionalPaidNarrative narrative,
            ProfessionalPaidNarrativeContext context) {
        Map<String, List<String>> textsById = new LinkedHashMap<>();
        for (OpportunityInsight item : narrative.opportunityInsights()) {
            List<String> texts = new ArrayList<>(List.of(item.explanation(), item.firstExperiment()));
            texts.addAll(item.practicalAiUses());
            texts.addAll(item.humanOwnership());
            textsById.put(item.id().strip(), texts);
        }
        narrative.humanValueInsights().forEach(item -> textsById.put(item.id().strip(),
                List.of(item.explanation(), item.strengthen())));
        for (var entry : textsById.entrySet()) {
            ResponsibilityCategory stored = context.categoryById().get(entry.getKey());
            for (String text : entry.getValue()) {
                String lower = text.toLowerCase(Locale.ROOT);
                for (ResponsibilityCategory other : ResponsibilityCategory.values()) {
                    // "AI may assist with parts" is also ordinary advice, so only its quoted, label-like use counts.
                    String label = other.label().toLowerCase(Locale.ROOT);
                    String named = other == ResponsibilityCategory.AI_ASSISTANCE_WITH_HUMAN_JUDGEMENT ? "\"" + label : label;
                    if (other != stored && lower.contains(named)) {
                        return Optional.of(text);
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<String> newRecommendation(List<String> prose, ProfessionalPaidNarrativeContext context) {
        Optional<String> invented = firstUndisclaimed(prose, NEW_RECOMMENDATION_CLAIMS, false);
        if (invented.isPresent()) {
            return invented;
        }
        for (String text : prose) {
            String lower = text.toLowerCase(Locale.ROOT);
            for (String label : context.unselectedSkillLabels()) {
                if (lower.contains(label.toLowerCase(Locale.ROOT))) {
                    return Optional.of(text);
                }
            }
        }
        return Optional.empty();
    }

    /** A claim not negated earlier in its sentence: "never use it without review" is a safeguard, not a delegation. */
    private static Optional<String> unnegated(List<String> prose, List<Pattern> claims) {
        for (String text : prose) {
            for (String sentence : SENTENCE_BREAK.split(text.strip())) {
                for (Pattern claim : claims) {
                    Matcher matcher = claim.matcher(sentence);
                    while (matcher.find()) {
                        if (!SAFETY_NEGATION.matcher(sentence.substring(0, matcher.start())).find()) {
                            return Optional.of(sentence.strip());
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<String> procedureDeviation(List<String> prose) {
        for (String text : prose) {
            for (String sentence : SENTENCE_BREAK.split(text.strip())) {
                Matcher deviation = DEVIATION.matcher(sentence);
                while (deviation.find()) {
                    String before = sentence.substring(0, deviation.start());
                    if (REGULATED.matcher(sentence).find() && !SAFETY_NEGATION.matcher(before).find()) {
                        return Optional.of(sentence.strip());
                    }
                }
            }
        }
        return Optional.empty();
    }

    /**
     * The summary must address you, and no sentence may describe you in the third person. A sentence need not begin
     * with "you": "These skills support your work" is second person.
     */
    private static Optional<String> secondPerson(ProfessionalPaidNarrative narrative, List<String> prose,
            String profession) {
        if (!SECOND_PERSON_WORDS.matcher(narrative.executiveSummary()).find()) {
            return Optional.of(narrative.executiveSummary());
        }
        String last = profession.strip().replaceAll(".*[\\s-]", "").replaceAll("[^A-Za-z]", "");
        String role = last.length() < 3 ? "worker" : Pattern.quote(last) + "s?";
        Pattern instruction = Pattern.compile(THIRD_PERSON_INSTRUCTION.formatted(THIRD_PERSON_SUBJECT.formatted(role)));
        return firstUndisclaimed(prose, List.of(THIRD_PERSON, instruction), false);
    }

    /** A claim about everyone with the job title, such as "nurses are" or "solicitors will". */
    private static Optional<Pattern> occupationWideClaim(String profession) {
        String last = profession.strip().replaceAll(".*\\s", "").replaceAll("[^A-Za-z-]", "");
        if (last.length() < 3) {
            return Optional.empty();
        }
        String plural = last.endsWith("s") ? last : last + "s";
        return Optional.of(Pattern.compile("(?i)\\b(?:all\\s+|most\\s+|many\\s+|typical\\s+)?" + Pattern.quote(plural)
                + "\\s+(?:are|tend|will|must|always|usually|typically|often|cannot|can't|need)\\b"));
    }

    private void claim(NarrativeRuleChecks checks, String rule, List<String> text, List<Pattern> claims,
            boolean disclaimable, String rejection) {
        Optional<String> found = firstUndisclaimed(text, claims, disclaimable);
        checks.check(rule, found.isPresent(), found::orElseThrow, rejection);
    }

    /** The first sentence that makes one of the claims; with {@code disclaimable}, an explicit denial lets it pass. */
    private static Optional<String> firstUndisclaimed(List<String> texts, List<Pattern> claims, boolean disclaimable) {
        for (String text : texts) {
            for (String sentence : SENTENCE_BREAK.split(text.strip())) {
                for (Pattern claim : claims) {
                    Matcher matcher = claim.matcher(sentence);
                    while (matcher.find()) {
                        if (!disclaimable || !disclaimed(sentence.substring(0, matcher.end()))) {
                            return Optional.of(sentence.strip());
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static boolean disclaimed(String before) {
        Matcher breaks = CLAUSE_BREAK.matcher(before);
        int clauseStart = 0;
        while (breaks.find()) {
            clauseStart = breaks.end();
        }
        return DISCLAIMER.matcher(before.substring(clauseStart)).find();
    }

    private static List<Pattern> patterns(String... regexes) {
        return Stream.of(regexes).map(regex -> Pattern.compile("(?i)" + regex)).toList();
    }
}
