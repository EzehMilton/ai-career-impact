package com.chikere.futureai.coursemodule.university.application;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The modules a caller has already selected for module-level analysis, with the course context they belong to.
 *
 * <p>Selection is the caller's job: this request accepts 1 to {@link #MAXIMUM_MODULES} modules and refuses more
 * rather than quietly keeping the first few. Each module gets a stable key, {@code m1} to {@code m8}, in the order
 * supplied. Preferred career areas are deliberately not part of the request, so they cannot influence module
 * judgments.</p>
 */
public record UniversityModuleAnalysisRequest(
        UUID assessmentId,
        String courseTitle,
        String courseDescription,
        List<String> modules,
        List<String> skillsDeveloped) {

    public static final int MAXIMUM_MODULES = 8;
    /** The same cap the University form applies to one module. */
    public static final int MAXIMUM_MODULE_LENGTH = 200;

    public UniversityModuleAnalysisRequest {
        Objects.requireNonNull(assessmentId, "assessmentId must not be null");
        courseTitle = required(courseTitle, "courseTitle");
        courseDescription = required(courseDescription, "courseDescription");
        Objects.requireNonNull(modules, "modules must not be null");
        if (modules.isEmpty()) {
            throw new IllegalArgumentException("at least one module must be selected for analysis");
        }
        if (modules.size() > MAXIMUM_MODULES) {
            throw new IllegalArgumentException("at most " + MAXIMUM_MODULES + " modules can be analysed in one "
                    + "request; " + modules.size() + " were supplied, and choosing which to analyse is the caller's "
                    + "decision");
        }
        modules = modules.stream().map(value -> {
            String title = required(value, "module");
            if (title.length() > MAXIMUM_MODULE_LENGTH) {
                throw new IllegalArgumentException("a module must be at most " + MAXIMUM_MODULE_LENGTH + " characters");
            }
            return title;
        }).toList();
        if (modules.stream().distinct().count() != modules.size()) {
            throw new IllegalArgumentException("each selected module must be distinct");
        }
        skillsDeveloped = skillsDeveloped == null ? List.of()
                : skillsDeveloped.stream().map(value -> required(value, "skill")).toList();
    }

    /** The stable key of the module at a 1-based index, for example {@code m3}. */
    public static String key(int index) {
        if (index < 1 || index > MAXIMUM_MODULES) {
            throw new IllegalArgumentException("module index must be between 1 and " + MAXIMUM_MODULES);
        }
        return "m" + index;
    }

    /** Modules by stable key, in the order supplied. */
    public Map<String, String> keyedModules() {
        LinkedHashMap<String, String> keyed = new LinkedHashMap<>();
        for (int index = 1; index <= modules.size(); index++) {
            keyed.put(key(index), modules.get(index - 1));
        }
        return Collections.unmodifiableMap(keyed);
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.strip();
    }
}
