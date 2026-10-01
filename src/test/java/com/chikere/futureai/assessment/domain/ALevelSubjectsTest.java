package com.chikere.futureai.assessment.domain;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class ALevelSubjectsTest {

    @Test
    void canonicalisesCommonAbbreviationsAndSpellings() {
        assertThat(ALevelSubjects.canonicalise("Maths")).isEqualTo("Mathematics");
        assertThat(ALevelSubjects.canonicalise("maths")).isEqualTo("Mathematics");
        assertThat(ALevelSubjects.canonicalise("  MATHS  ")).isEqualTo("Mathematics");
        assertThat(ALevelSubjects.canonicalise("Further Maths")).isEqualTo("Further Mathematics");
        assertThat(ALevelSubjects.canonicalise("comp sci")).isEqualTo("Computer Science");
        assertThat(ALevelSubjects.canonicalise("Computing")).isEqualTo("Computer Science");
        assertThat(ALevelSubjects.canonicalise("bio")).isEqualTo("Biology");
        assertThat(ALevelSubjects.canonicalise("English  Lit")).isEqualTo("English Literature");
        assertThat(ALevelSubjects.canonicalise("government and politics")).isEqualTo("Politics");
        assertThat(ALevelSubjects.canonicalise("PE")).isEqualTo("Physical Education");
        assertThat(ALevelSubjects.canonicalise("A-level Chemistry")).isEqualTo("Chemistry");
        assertThat(ALevelSubjects.canonicalise("A level chem")).isEqualTo("Chemistry");
    }

    @Test
    void acceptsAnyUnknownSubjectSafelyRatherThanRejectingIt() {
        // A student must never be blocked because their school offers something unusual.
        assertThat(ALevelSubjects.canonicalise("classical civilisation"))
                .isEqualTo("Classical Civilisation");
        assertThat(ALevelSubjects.canonicalise("environmental science")).isEqualTo("Environmental Science");
        assertThat(ALevelSubjects.canonicalise("  mandarin   chinese ")).isEqualTo("Mandarin Chinese");
        // Odd input is preserved rather than rejected; HTML escaping is the template's job.
        assertThat(ALevelSubjects.canonicalise("<script>alert(1)</script>"))
                .isEqualTo("<Script>alert(1)</Script>");
    }

    @Test
    void isIdempotentSoRepeatedCanonicalisationIsSafe() {
        for (String subject : new String[] {"Maths", "comp sci", "english lang", "Unusual Subject"}) {
            String once = ALevelSubjects.canonicalise(subject);
            assertThat(ALevelSubjects.canonicalise(once)).isEqualTo(once);
        }
    }

    @Test
    void rejectsOnlyBlankInput() {
        assertThatIllegalArgumentException().isThrownBy(() -> ALevelSubjects.canonicalise("   "));
        assertThatNullPointerException().isThrownBy(() -> ALevelSubjects.canonicalise(null));
    }

    @Test
    void containsNoHiddenSubjectRankingOrScoring() throws Exception {
        // The alias table may only map text to text. A numeric value here would be a secret
        // subject-ranking engine, which this journey must never have.
        Field aliases = ALevelSubjects.class.getDeclaredField("ALIASES");
        aliases.setAccessible(true);
        Map<?, ?> table = (Map<?, ?>) aliases.get(null);

        assertThat(table).isNotEmpty();
        assertThat(table.keySet()).allSatisfy(key -> assertThat(key).isInstanceOf(String.class));
        assertThat(table.values()).allSatisfy(value -> assertThat(value).isInstanceOf(String.class));
        assertThat(ALevelSubjects.class.getDeclaredFields())
                .noneMatch(field -> field.getType().isAssignableFrom(Number.class));
        for (Field field : ALevelSubjects.class.getDeclaredFields()) {
            assertThat(field.getType()).isNotIn(int.class, double.class, long.class, float.class);
        }
    }
}
