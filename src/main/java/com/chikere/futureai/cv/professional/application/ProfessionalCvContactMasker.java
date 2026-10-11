package com.chikere.futureai.cv.professional.application;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Removes contact details from CV text before it leaves the application for profile extraction.
 *
 * <p>Only email addresses, web links, phone numbers and full UK postcodes are replaced. Names and
 * street addresses are deliberately left alone: they cannot be recognised reliably, and job titles,
 * employers, dates and duties must reach the extractor unchanged. A phone-shaped run of digits that
 * is really an employment date or date range is never touched.</p>
 */
public final class ProfessionalCvContactMasker {
    // Every pattern can only start at a token boundary and is length-bounded, so masking stays linear
    // even on a long unbroken run of characters.
    static final Pattern EMAIL = Pattern.compile(
            "(?<![A-Za-z0-9._%+-])[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9-]{1,63}(?:\\.[A-Za-z0-9-]{1,63}){0,8}"
                    + "\\.[A-Za-z]{2,24}");
    static final Pattern LINK = Pattern.compile(
            "(?i)(?:\\bhttps?://|\\bwww\\.|\\b(?:[a-z0-9-]+\\.)?(?:linkedin|github)\\.com/)"
                    + "[^\\s<>()|\"']*[^\\s<>()|\"'.,;:!?]");
    static final Pattern POSTCODE = Pattern.compile(
            "\\b(?:GIR 0AA|[A-Z]{1,2}[0-9][A-Z0-9]? [0-9][ABD-HJLNP-UW-Z]{2})\\b");
    // 10 to 20 digits. The upper bound matters: an unbounded repeat recurses once per digit and would
    // overflow the stack on a long digit run.
    static final Pattern PHONE = Pattern.compile(
            "(?<![\\w+(])(?:\\+|\\()?\\d(?:[ .\\-()]{0,2}\\d){9,19}\\)?(?![\\w])");

    private ProfessionalCvContactMasker() {}

    public static String mask(String text) {
        if (text == null || text.isEmpty()) return text;
        // Emails first, so an address at linkedin.com or github.com is not mistaken for a link.
        String masked = EMAIL.matcher(text).replaceAll("[email]");
        masked = LINK.matcher(masked).replaceAll("[link]");
        masked = POSTCODE.matcher(masked).replaceAll("[postcode]");
        return maskPhones(masked);
    }

    private static String maskPhones(String text) {
        Matcher matcher = PHONE.matcher(text);
        var result = new StringBuilder();
        while (matcher.find()) {
            String candidate = matcher.group();
            boolean date = ProfessionalExperienceCalculator.isDateOrDateRange(candidate);
            matcher.appendReplacement(result, date ? Matcher.quoteReplacement(candidate) : "[phone]");
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
