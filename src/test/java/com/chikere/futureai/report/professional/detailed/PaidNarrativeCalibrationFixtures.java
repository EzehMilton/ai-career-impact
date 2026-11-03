package com.chikere.futureai.report.professional.detailed;

import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrative;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrative.AdaptationStrategy;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrative.HumanValueInsight;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrative.ItemExplanation;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrative.OpportunityInsight;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrative.SkillInsight;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * professional-paid-narrative-v2 calibration fixtures: hand-written examples of the specificity the paid narrative is
 * expected to reach, for the captured Registered Nurse and Management Accountant chains. They are TEST DATA ONLY,
 * written against the ids and stored signals of those chains, used to render the offline reports reviewed for quality
 * and to hold the validator to realistic practical copy. No production code reads this wording.
 */
public final class PaidNarrativeCalibrationFixtures {

    public static final String NURSE = "registered-nurse-8";
    public static final String ACCOUNTANT = "management-accountant-8";

    private PaidNarrativeCalibrationFixtures() {
    }

    /** The calibrated narrative for a captured run, if one is written. */
    public static Optional<ProfessionalPaidNarrative> forRun(String run) {
        return Optional.ofNullable(Map.of(NURSE, registeredNurse(), ACCOUNTANT, managementAccountant()).get(run));
    }

    public static ProfessionalPaidNarrative registeredNurse() {
        return new ProfessionalPaidNarrative(
                "Most of your work as you described it happens at the bedside: monitoring patients, giving medication, "
                        + "responding when things change and supporting patients and families. Those responsibilities "
                        + "depend on your presence, your clinical judgement and your accountability, and the analysis found "
                        + "little scope for AI in them.\n\n"
                        + "The clearest place where AI may assist is the documentation around that care. Structuring a record "
                        + "from information you have already gathered, checking it for missing or inconsistent entries and "
                        + "pulling existing notes together before a handover could save you time, provided you verify every "
                        + "entry against what you actually observed.\n\n"
                        + "Coordinating care with colleagues offers a second, smaller opening: organising information before "
                        + "handovers and team conversations, while the conversation and every clinical decision stay yours.",
                "Read together, your low AI Exposure and moderate Human Advantage describe work whose value sits mostly in "
                        + "what you do in person. Your moderate AI Complementarity points to the paperwork and preparation "
                        + "around care rather than the care itself, and your moderate Role Adaptability suggests room to fold "
                        + "small, careful changes into how you document and prepare.",
                "Of your eight responsibilities, four are mainly hands-on, three are human-led with AI in a supporting "
                        + "role, and only recording observations and clinical information stood out as a clear opening for "
                        + "AI assistance. Every one carries significant accountability, which is why close human oversight "
                        + "applies wherever AI is involved.",
                List.of(
                        new OpportunityInsight("opportunity-1-r3",
                                "Much of the effort in this responsibility goes into turning what you have observed into "
                                        + "complete, consistent records, which is where careful support around "
                                        + "documentation may help.",
                                List.of("Structuring a draft entry from observations you have already recorded on approved "
                                                + "forms, for you to verify",
                                        "Checking a completed record for missing fields, unsigned entries or values that do "
                                                + "not match earlier entries",
                                        "Summarising existing notes into a short overview you check before handover",
                                        "Tidying non-clinical wording such as appointment or discharge-letter details"),
                                List.of("Verifying every entry against the patient you saw and the observations you took",
                                        "Interpreting what the observations mean and recognising signs of deterioration",
                                        "Deciding whether a concern needs escalating, and to whom"),
                                "On a few shifts, use an approved tool only to check records you have already completed for "
                                        + "missing or inconsistent fields, and note how many genuine gaps it found, how many "
                                        + "false alarms it raised and how long the check took."),
                        new OpportunityInsight("opportunity-2-r6",
                                "Coordinating care is led by your conversations with colleagues, but each one depends on "
                                        + "having the right information organised beforehand, and that preparation is where "
                                        + "AI may play a small supporting part.",
                                List.of("Pulling existing notes into a structured handover summary that you check before "
                                                + "using it",
                                        "Organising outstanding tasks and pending results into a checklist for a team meeting",
                                        "Drafting the layout of a referral or transfer note from information already in the "
                                                + "record, for your review"),
                                List.of("What is said at handover and how concerns are communicated to colleagues",
                                        "Agreeing the plan of care with the wider team",
                                        "Checking that nothing clinically important has been left out of a summary"),
                                "Before a handful of handovers, prepare a summary with approved support from notes already "
                                        + "in the record, then compare it with your own handover notes and record what it "
                                        + "missed, what it got wrong and whether it saved preparation time.")),
                List.of(
                        new HumanValueInsight("human-value-1-r5",
                                "Supporting patients and families depends on reading how someone is coping, judging what "
                                        + "they need to hear and when, and being physically there; that trust is built in "
                                        + "person and sits at the centre of the care you give.",
                                "Keep time for unhurried conversations, and use any time saved on documentation to protect "
                                        + "them rather than fill it with more paperwork."),
                        new HumanValueInsight("human-value-2-r2",
                                "Giving medication safely rests on your own checks at the point of care: the right patient, "
                                        + "the right medicine and the right moment, with full accountability if anything "
                                        + "goes wrong.",
                                "Keep your checking routine at the point of administration entirely your own, separate from "
                                        + "any AI-assisted paperwork around it."),
                        new HumanValueInsight("human-value-3-r7",
                                "Emergencies call for fast judgement in incomplete situations, physical action and calm "
                                        + "communication with patients and colleagues, all at once and in person.",
                                "Keep your situational awareness sharp through simulation practice and debriefs after real "
                                        + "events, so your response stays practised whatever happens to the paperwork.")),
                List.of(
                        new SkillInsight("skill-1-responsible-ai-use",
                                "Because every responsibility you listed needs close human oversight, how any AI tool is "
                                        + "used in your work matters as much as whether it is used.",
                                List.of("Knowing which tools your organisation has approved for patient information, and "
                                                + "using only those",
                                        "Keeping patient-identifiable details out of anything not approved for them",
                                        "Being able to explain to a colleague where AI support was used in a record and how "
                                                + "you checked it")),
                        new SkillInsight("skill-2-hands-on-practice-and-safety",
                                "Much of your work is physical and in person, so practical clinical skill and safe practice "
                                        + "stay at the core of what you do.",
                                List.of("Keeping your observation and medication skills current through practice and "
                                                + "supervision",
                                        "Following the safety checks in your clinical procedures exactly, however the "
                                                + "paperwork is produced")),
                        new SkillInsight("skill-3-communication-and-relationships",
                                "Escalating concerns, coordinating care and supporting families all depend on how clearly "
                                        + "and calmly you communicate.",
                                List.of("Giving clear, structured handovers and escalation calls that colleagues can act on",
                                        "Explaining what is happening to patients and families in plain language")),
                        new SkillInsight("skill-4-verification-and-quality-checking",
                                "Wherever AI assists with records or summaries, your checking is what keeps them accurate "
                                        + "and safe.",
                                List.of("Checking AI-assisted documentation against your actual observations before it "
                                                + "enters the record",
                                        "Confirming that a summary has not dropped clinically important information",
                                        "Noticing when a generated draft conflicts with what you saw at the bedside"))),
                "The plan starts by confirming what your organisation allows and mapping where your documentation time "
                        + "goes, moves to one small, closely checked trial on recording observations, and ends by keeping "
                        + "only what helped.",
                List.of(
                        new ItemExplanation("action-1-p1-confirm-policy-and-tools", "Ask your manager which tools, if "
                                + "any, are approved for patient information and what they may be used for, before you "
                                + "try anything."),
                        new ItemExplanation("action-1-p1-map-current-workflow", "Over a week, note where your recording "
                                + "time goes: transcribing observations, completing forms, re-entering the same details "
                                + "and writing handover notes."),
                        new ItemExplanation("action-1-p1-define-human-review", "List the checks that are always yours, "
                                + "such as matching each entry to the patient and the observation, and treat them as fixed."),
                        new ItemExplanation("action-1-p1-note-human-contribution", "Notice the moments with patients, "
                                + "families and medication that better paperwork would not shorten, so any time saved goes "
                                + "back into them."),
                        new ItemExplanation("action-2-p2-pilot-with-review", "Test approved support only on checking "
                                + "records you have already completed, using your own finished entries as the input. Review "
                                + "every flag yourself, and judge the trial on the time the check took, the genuine gaps it "
                                + "found, the false alarms you dismissed and whether your records became more complete."),
                        new ItemExplanation("action-2-p2-record-what-works", "Keep a short log after each shift: what "
                                + "the check caught, what it missed and any correction you had to make."),
                        new ItemExplanation("action-2-p2-practise-human-skill", "Use supervision or reflective practice "
                                + "to sharpen your observation and medication routines, separately from the trial."),
                        new ItemExplanation("action-3-p3-adopt-or-stop", "If the check found real gaps without adding "
                                + "effort, turn it into a short personal checklist; if it mostly raised false alarms, stop."),
                        new ItemExplanation("action-3-p3-document-safeguards", "Write a few lines on how you use AI "
                                + "support: approved tools only, your verification of every entry, and how you would raise "
                                + "a concern."),
                        new ItemExplanation("action-3-p3-choose-next-opportunity", "Try handover preparation for "
                                + "coordinating care next, with the same small, checked approach."),
                        new ItemExplanation("action-3-p3-strengthen-human-contribution", "Ask a colleague for feedback on "
                                + "how you support families and give medication, and protect time for both.")),
                new AdaptationStrategy(
                        List.of("Your direct observation of patients and your recognition of deterioration",
                                "The trust patients and families place in your face-to-face care"),
                        List.of("Checking AI-assisted documentation quickly and reliably against your own observations",
                                "Which tools your organisation approves for patient information, and their limits"),
                        List.of("Checking completed records for missing or inconsistent fields",
                                "Preparing handover summaries from existing notes, checked before you use them"),
                        List.of("Clinical judgement in changing situations",
                                "Calm, clear communication during escalations and emergencies"),
                        List.of("How documentation support develops in the tools your organisation approves",
                                "Any change in how handover or referral information is prepared where you work")),
                "The value of your work sits at the bedside. Used carefully, support around records and handovers may give "
                        + "you back some time; the aim is to spend it on the patients in front of you.");
    }

    public static ProfessionalPaidNarrative managementAccountant() {
        return new ProfessionalPaidNarrative(
                "Much of your work involves assembling, analysing and explaining financial information, which is why "
                        + "your AI Exposure is high: month-end preparation, management reporting and budget analysis all "
                        + "include steps where AI may assist under your review.\n\n"
                        + "The value you add sits around those steps: deciding which numbers matter commercially, "
                        + "challenging explanations and turning figures into advice that managers act on. Your meetings with "
                        + "department managers are where that judgement and relationship work is most visible.\n\n"
                        + "The practical opportunity is AI help with first drafts, reconciliation checks and variance "
                        + "groupings, while you keep ownership of the conclusions, the commentary and the sign-off.",
                "Read together, your high AI Exposure and moderate AI Complementarity describe work with a lot of "
                        + "information handling where AI may assist alongside you. Your moderate Human Advantage reflects "
                        + "the judgement and relationship work in your role, and your moderate Role Adaptability suggests you "
                        + "can bring careful changes into your monthly cycle step by step.",
                "Four of your eight responsibilities are good opportunities for AI assistance and three are work where AI "
                        + "may assist with parts while your judgement leads; only meeting department managers is human-led. "
                        + "Most carry significant accountability, so the figures and conclusions you publish need close "
                        + "human oversight wherever AI assists.",
                List.of(
                        new OpportunityInsight("opportunity-1-r1",
                                "Month-end involves a lot of repeatable assembly and checking of figures, which is where AI "
                                        + "support may save time while you keep control of the result.",
                                List.of("Producing a first-pass list of balances that do not agree between the ledger and "
                                                + "supporting schedules",
                                        "Flagging unusual journals, accrual movements or balances compared with prior "
                                                + "months, for you to investigate",
                                        "Drafting the routine sections of the month-end pack from figures you have already "
                                                + "finalised",
                                        "Building a checklist of month-end tasks and open items from last month's pack"),
                                List.of("Judging accruals, provisions and other estimates",
                                        "Investigating and correcting anything flagged before the accounts close",
                                        "Approving the final management accounts"),
                                "For one month-end, run the reconciliation check alongside your usual process and compare "
                                        + "the items it flagged with the ones you found yourself, noting misses, false "
                                        + "alarms and the time it took."),
                        new OpportunityInsight("opportunity-2-r3",
                                "Management reports combine a fixed structure with commentary, so AI may help with drafting "
                                        + "and consistency checks while you decide what the report says.",
                                List.of("Drafting first-pass commentary on movements you have already identified, for you to "
                                                + "rewrite",
                                        "Checking that figures quoted in the narrative match the tables and appendices",
                                        "Turning a detailed report into a short summary for a non-finance audience",
                                        "Keeping terminology and formatting consistent across monthly reports"),
                                List.of("Choosing which messages matter commercially and how they are framed",
                                        "Checking every figure in the final report against the source numbers",
                                        "Signing off the report before it is circulated"),
                                "Take one report you have already published, use an approved tool to draft commentary from "
                                        + "its tables, and compare that draft with your own: note what was wrong, what was "
                                        + "missing and how long correcting it took."),
                        new OpportunityInsight("opportunity-3-r2",
                                "Budget-versus-actual analysis follows a regular pattern each month, so AI may help surface "
                                        + "and organise variances while you work out what they mean.",
                                List.of("Flagging material variances against budget, forecast and the previous period for "
                                                + "your review",
                                        "Grouping recurring variance explanations so repeat issues are easy to see",
                                        "Drafting questions to raise with budget holders about unexplained movements",
                                        "Preparing a first-pass variance table with draft commentary for you to challenge"),
                                List.of("Judging whether a variance is commercially significant",
                                        "Investigating the true operational cause and challenging departmental explanations",
                                        "Deciding whether forecasts or assumptions should change"),
                                "Using last quarter's budget and actuals, produce a draft list of material variances with "
                                        + "approved support and compare it with the analysis you actually did, noting what "
                                        + "it missed, what it flagged wrongly and how much checking it needed.")),
                List.of(
                        new HumanValueInsight("human-value-1-r6",
                                "These meetings are where numbers become decisions: you read how a manager is responding, "
                                        + "probe explanations that do not add up and build the trust that makes your advice "
                                        + "land.",
                                "Spend some of the time saved on preparation thinking through the two or three questions "
                                        + "each meeting needs, and practise challenging constructively rather than simply "
                                        + "reporting.")),
                List.of(
                        new SkillInsight("skill-1-responsible-ai-use",
                                "Your work involves formal reporting obligations and commercially sensitive figures, so how "
                                        + "AI is used matters as much as whether it is used.",
                                List.of("Using only approved tools for ledger extracts, budgets and other sensitive figures",
                                        "Recording where AI support contributed to a pack or report so reviewers can "
                                                + "follow it")),
                        new SkillInsight("skill-2-verification-and-quality-checking",
                                "Wherever AI drafts or analyses figures, your checking is what keeps the numbers you publish "
                                        + "reliable.",
                                List.of("Checking AI-generated variance commentary against the source numbers",
                                        "Validating assumptions before they go into a forecast",
                                        "Confirming that a generated explanation matches the underlying ledger and reports")),
                        new SkillInsight("skill-3-ai-assisted-workflow",
                                "Several of your responsibilities follow a monthly cycle with clear scope for assistance, so "
                                        + "fitting AI support into that cycle deliberately is worth the effort.",
                                List.of("Choosing which month-end steps get AI support and which stay manual",
                                        "Building reusable instructions and templates for recurring variance and "
                                                + "commentary drafts")),
                        new SkillInsight("skill-4-communication-and-relationships",
                                "Your meetings with department managers rely on explaining figures clearly and handling "
                                        + "challenge well.",
                                List.of("Explaining what a variance means for a manager's decisions in plain language",
                                        "Challenging a budget holder's explanation constructively without damaging the "
                                                + "relationship"))),
                "The plan starts by confirming which tools you may use with financial data, tries AI support on one "
                        + "month-end, and keeps only what proves accurate and saves time.",
                List.of(
                        new ItemExplanation("action-1-p1-confirm-policy-and-tools", "Check which tools your "
                                + "organisation allows for ledger extracts and management information, and what must stay "
                                + "within approved tools."),
                        new ItemExplanation("action-1-p1-map-current-workflow", "Map your month-end timetable step by "
                                + "step and mark the steps that are repetitive assembly, reconciliation or first-draft "
                                + "writing."),
                        new ItemExplanation("action-1-p1-define-human-review", "List what is always yours: estimates and "
                                + "judgements, the final figures, the commentary and the sign-off."),
                        new ItemExplanation("action-1-p1-note-human-contribution", "Note which parts of your meetings "
                                + "with department managers depend on challenge and relationship, and keep AI out of them."),
                        new ItemExplanation("action-2-p2-pilot-with-review", "Test AI support on reconciliation and "
                                + "variance flagging for one month-end, using last month's closed figures and this month's "
                                + "ledger extracts as inputs. Review every flagged item yourself, and judge the trial on "
                                + "time saved, accuracy, the number of corrections you made, how much review effort it "
                                + "needed and whether the pack's quality improved or declined."),
                        new ItemExplanation("action-2-p2-record-what-works", "Keep a short note per step: time taken, "
                                + "items flagged correctly, false alarms and corrections."),
                        new ItemExplanation("action-2-p2-practise-human-skill", "In your next meetings with department "
                                + "managers, practise opening with a question about the business before the numbers."),
                        new ItemExplanation("action-3-p3-adopt-or-stop", "Keep the steps that saved time without "
                                + "lowering accuracy as a written month-end routine, and drop the rest."),
                        new ItemExplanation("action-3-p3-document-safeguards", "Write down your safeguards: approved "
                                + "tools only, every figure traced to source, and your sign-off before anything is "
                                + "circulated."),
                        new ItemExplanation("action-3-p3-choose-next-opportunity", "Management reporting comes next: "
                                + "apply the same approach to commentary drafting on one report."),
                        new ItemExplanation("action-3-p3-strengthen-human-contribution", "Ask one or two managers how "
                                + "useful your explanations are, and adjust how you present variances.")),
                new AdaptationStrategy(
                        List.of("Your commercial judgement about which numbers matter",
                                "The trust department managers place in your challenge and advice"),
                        List.of("Checking AI-assisted figures and commentary efficiently against source data",
                                "Writing clear, reusable instructions for recurring drafts"),
                        List.of("Reconciliation and variance flagging on one month-end",
                                "First-pass commentary on a report you have already published"),
                        List.of("Questioning and challenge in conversations with budget holders",
                                "Explaining financial results in terms of business decisions"),
                        List.of("How AI support in your organisation's approved tools changes month-end preparation",
                                "Whether drafting support changes what managers expect from your commentary")),
                "Your work has real scope for AI assistance, and that is a practical opportunity rather than a verdict. "
                        + "The more of the assembly AI may support, the more your value rests on judgement, challenge and "
                        + "the advice managers act on.");
    }
}
