package com.chikere.futureai.narrative.professional.paid;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The live Nurse v4 plan notes that only repeated their action, and the kinds of note that add something. */
class PlanNoteRestatementTest {

    private static final String POLICY = "Confirm your organisation's policy and approved tools before trying any AI "
            + "assistance.";
    private static final String POLICY_RATIONALE = "Formal obligations or close oversight apply to parts of this work.";
    private static final String MAP = "Map how you currently carry out “Record observations and clinical information” "
            + "and “Coordinate care with other healthcare professionals”, noting which steps involve preparing, "
            + "drafting, checking or organising information.";
    private static final String DEFINE = "Write down what must always be checked or decided by you in those "
            + "responsibilities.";
    private static final String PILOT = "Try AI assistance on “Record observations and clinical information” for a few "
            + "weeks, reviewing every output, and compare time, quality and errors with your usual approach.";

    @Test
    void identicalWordingPunctuationChangesAndSmallRephrasingsRestateTheAction() {
        assertThat(PlanNoteRestatement.restates(POLICY, POLICY, POLICY_RATIONALE)).isTrue();
        assertThat(PlanNoteRestatement.restates("Confirm your organisation’s policy, and approved tools, before trying "
                + "any AI assistance!", POLICY)).isTrue();
        assertThat(PlanNoteRestatement.restates("Before you try AI assistance, confirm the policy of your organisation "
                + "and which tools are approved.", POLICY)).isTrue();
        assertThat(PlanNoteRestatement.restates("Map how you carry out “Record observations and clinical information” and "
                + "“Coordinate care with other healthcare professionals”, noting the steps that involve drafting or "
                + "checking information.", MAP)).isTrue();
        assertThat(PlanNoteRestatement.restates("Write down what you must always check or decide yourself in these "
                + "responsibilities.", DEFINE)).isTrue();
        assertThat(PlanNoteRestatement.restates("Try AI assistance for a few weeks, reviewing every output and "
                + "comparing time, quality and errors with your usual approach.", PILOT)).isTrue();
    }

    @Test
    void aNoteThatSaysWhyWhatToReviewOrWhatSuccessLooksLikeIsKept() {
        assertThat(PlanNoteRestatement.restates("Ask which patient information may be used with approved tools, and "
                + "whether drafts must be marked as AI-assisted in the record.", POLICY, POLICY_RATIONALE)).isFalse();
        assertThat(PlanNoteRestatement.restates("Look especially at handover preparation and end-of-shift record "
                + "completion, where most of your writing time goes.", MAP)).isFalse();
        assertThat(PlanNoteRestatement.restates("Use last month's anonymised handover notes as inputs, check each draft "
                + "against the record, and judge success by fewer missing fields and less rework.", PILOT)).isFalse();
        // Restating the action and then adding one concrete point is still worth showing.
        assertThat(PlanNoteRestatement.restates(POLICY.replace(".", ", and note which record fields must never be "
                + "entered into any tool."), POLICY)).isFalse();
    }
}
