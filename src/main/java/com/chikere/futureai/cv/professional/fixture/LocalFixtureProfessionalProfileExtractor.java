package com.chikere.futureai.cv.professional.fixture;

import com.chikere.futureai.cv.professional.application.*;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class LocalFixtureProfessionalProfileExtractor implements ProfessionalProfileExtractor {
    private final String fixtureId;
    private final ProfessionalExperienceCalculator experienceCalculator;

    public LocalFixtureProfessionalProfileExtractor(String fixtureId) {
        this(fixtureId, Clock.systemUTC());
    }

    public LocalFixtureProfessionalProfileExtractor(String fixtureId, Clock clock) {
        this.fixtureId = fixtureId;
        this.experienceCalculator = new ProfessionalExperienceCalculator(clock);
    }

    @Override
    public ProfessionalProfile extract(String ignoredCvText) {
        return switch (fixtureId) {
            case "senior_software_architect" -> profile("Senior Software Architect",
                    "Northbridge Digital", "March 2019",
                    "Designs complex software platforms, resolves technical trade-offs and guides engineering teams.",
                    List.of("Design dependable software architecture", "Review technical designs", "Guide delivery teams"),
                    List.of("Software architecture", "Cloud platforms", "Systems integration"),
                    List.of("Communication", "Mentoring"),
                    List.of(new ProfessionalEmploymentEntry("Software Engineer", "Kestrel Systems",
                            "September 2012", "February 2019", true)));
            case "routine_software_developer" -> profile("Software Developer",
                    "Harbour Lane Software", "June 2022",
                    "Implements and maintains business software from established requirements and team standards.",
                    List.of("Implement application features", "Fix defects", "Write automated tests"),
                    List.of("Java", "SQL", "Version control"), List.of("Teamwork"), null);
            case "electrician" -> profile("Electrician",
                    "Brightline Electrical Services", "April 2016",
                    "Installs, inspects and repairs electrical systems while applying safety rules and site judgement.",
                    List.of("Install electrical systems", "Diagnose faults", "Complete safety checks"),
                    List.of("Electrical testing", "Wiring"), List.of("Client communication"),
                    List.of(new ProfessionalEmploymentEntry("Apprentice Electrician", "Calder Contracting",
                            "September 2013", "March 2016", true)));
            case "registered_nurse" -> profile("Registered Nurse",
                    "St Aldric's Hospital", "September 2017",
                    "Provides direct patient care, monitors changing needs and coordinates safely with clinical teams.",
                    List.of("Assess patient needs", "Administer care", "Maintain clinical records"),
                    List.of("Clinical assessment"), List.of("Empathy", "Communication"),
                    List.of(new ProfessionalEmploymentEntry("Retail Assistant", "Market Row Stores",
                            "June 2014", "August 2017", false)));
            default -> throw new ProfessionalProfileExtractionException(
                    "Unsupported local professional profile fixture: " + fixtureId);
        };
    }

    @Override
    public String version() {
        return ProfessionalCvVersions.EXTRACTOR_FIXTURE_V1;
    }

    /**
     * Years are calculated from the fixture's own dated history, exactly as for a real CV. A null
     * earlier history stands for a CV without usable dates, so years stay unknown.
     */
    private ProfessionalProfile profile(String role, String employer, String start, String summary,
            List<String> responsibilities, List<String> technical, List<String> human,
            List<ProfessionalEmploymentEntry> earlierJobs) {
        var experience = ProfessionalExperience.unknown();
        if (earlierJobs != null) {
            var history = new ArrayList<ProfessionalEmploymentEntry>();
            history.add(new ProfessionalEmploymentEntry(role, employer, start, "Present", true));
            history.addAll(earlierJobs);
            String fixtureText = history.stream().map(job -> job.start() + " - " + job.end())
                    .collect(Collectors.joining("\n"));
            experience = experienceCalculator.calculate(history, start, "Present", fixtureText);
        }
        return new ProfessionalProfile(role, employer, start, "Present", summary, responsibilities, technical,
                human, List.of(), List.of(), List.of(), experience.years(), experience.basis(),
                experience.excluded(),
                ProfessionalProfileCompleteness.COMPLETE, List.of());
    }
}
