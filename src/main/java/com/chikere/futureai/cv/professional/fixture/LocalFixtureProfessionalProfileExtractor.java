package com.chikere.futureai.cv.professional.fixture;

import com.chikere.futureai.cv.professional.application.*;

import java.util.List;
import java.util.OptionalInt;

public final class LocalFixtureProfessionalProfileExtractor implements ProfessionalProfileExtractor {
    private final String fixtureId;

    public LocalFixtureProfessionalProfileExtractor(String fixtureId) {
        this.fixtureId = fixtureId;
    }

    @Override
    public ProfessionalProfile extract(String ignoredCvText) {
        return switch (fixtureId) {
            case "senior_software_architect" -> profile("Senior Software Architect",
                    "Designs complex software platforms, resolves technical trade-offs and guides engineering teams.",
                    List.of("Design dependable software architecture", "Review technical designs", "Guide delivery teams"),
                    List.of("Software architecture", "Cloud platforms", "Systems integration"),
                    List.of("Communication", "Mentoring"), OptionalInt.of(14));
            case "routine_software_developer" -> profile("Software Developer",
                    "Implements and maintains business software from established requirements and team standards.",
                    List.of("Implement application features", "Fix defects", "Write automated tests"),
                    List.of("Java", "SQL", "Version control"), List.of("Teamwork"), OptionalInt.empty());
            case "electrician" -> profile("Electrician",
                    "Installs, inspects and repairs electrical systems while applying safety rules and site judgement.",
                    List.of("Install electrical systems", "Diagnose faults", "Complete safety checks"),
                    List.of("Electrical testing", "Wiring"), List.of("Client communication"), OptionalInt.of(9));
            case "registered_nurse" -> profile("Registered Nurse",
                    "Provides direct patient care, monitors changing needs and coordinates safely with clinical teams.",
                    List.of("Assess patient needs", "Administer care", "Maintain clinical records"),
                    List.of("Clinical assessment"), List.of("Empathy", "Communication"), OptionalInt.of(8));
            default -> throw new ProfessionalProfileExtractionException(
                    "Unsupported local professional profile fixture: " + fixtureId);
        };
    }

    private ProfessionalProfile profile(String role, String summary, List<String> responsibilities,
            List<String> technical, List<String> human, OptionalInt years) {
        return new ProfessionalProfile(role, summary, responsibilities, technical, human,
                List.of(), List.of(), List.of(), years, ProfessionalProfileCompleteness.COMPLETE, List.of());
    }
}
