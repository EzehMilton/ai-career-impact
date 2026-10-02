package com.chikere.futureai.assessment.web;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;

/**
 * Bounds a textarea that the form mappers later split into a list.
 *
 * <p>A character limit alone leaves the shape of the input unbounded: four thousand characters can
 * arrive as two thousand one-character entries, each of which becomes a list item carried through
 * judgment, persistence and rendering. This caps how many entries a field may produce and how long
 * any single one may be, so the cost of a submission is bounded in items as well as in bytes.</p>
 *
 * <p>Entries are counted the way the matching form mapper splits them, so the message a person sees
 * matches the list the application actually builds.</p>
 */
@Documented
@Constraint(validatedBy = BoundedTextListValidator.class)
@Target({FIELD, ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface BoundedTextList {

    String message() default "Shorten this list.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /** Greatest number of non-empty entries allowed. */
    int maxItems();

    /** Greatest length allowed for any single entry, after trimming. */
    int maxItemLength();

    /** What the matching form mapper splits on. */
    Separator separator() default Separator.NEW_LINE;

    enum Separator {
        NEW_LINE("\\R"),
        NEW_LINE_OR_COMMA("\\R|,");

        private final String pattern;

        Separator(String pattern) {
            this.pattern = pattern;
        }

        String pattern() {
            return pattern;
        }
    }
}
