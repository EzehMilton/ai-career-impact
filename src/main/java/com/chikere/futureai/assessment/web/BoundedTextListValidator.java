package com.chikere.futureai.assessment.web;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Arrays;
import java.util.List;

/**
 * Counts entries exactly as the form mappers do — split, trim, drop blanks — so a field that passes
 * here cannot produce a longer list downstream.
 */
public final class BoundedTextListValidator implements ConstraintValidator<BoundedTextList, String> {

    private int maxItems;
    private int maxItemLength;
    private String separator;

    public BoundedTextListValidator() {
        // Instantiated reflectively by the validation provider.
    }

    @Override
    public void initialize(BoundedTextList constraint) {
        this.maxItems = constraint.maxItems();
        this.maxItemLength = constraint.maxItemLength();
        this.separator = constraint.separator().pattern();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            // Whether the field is required at all is @NotBlank's business, not this constraint's.
            return true;
        }
        List<String> entries = Arrays.stream(value.split(separator))
                .map(String::strip)
                .filter(entry -> !entry.isEmpty())
                .toList();
        if (entries.size() > maxItems) {
            return fail(context, "Enter no more than " + maxItems + " entries, one per line.");
        }
        if (entries.stream().anyMatch(entry -> entry.length() > maxItemLength)) {
            return fail(context, "Keep each entry under " + maxItemLength + " characters.");
        }
        return true;
    }

    private boolean fail(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
        return false;
    }
}
