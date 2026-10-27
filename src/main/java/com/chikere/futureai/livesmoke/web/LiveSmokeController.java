package com.chikere.futureai.livesmoke.web;

import com.chikere.futureai.configuration.ApplicationProfiles;
import com.chikere.futureai.livesmoke.LiveSmokeCase;
import com.chikere.futureai.livesmoke.LiveSmokeCaseCatalog;
import com.chikere.futureai.livesmoke.LiveSmokeJourney;
import com.chikere.futureai.livesmoke.LiveSmokeProviderStatus;
import com.chikere.futureai.livesmoke.LiveSmokeRuns;
import com.chikere.futureai.livesmoke.LiveSmokeTraceFiles;
import com.chikere.futureai.responsibility.professional.analysis.ProfessionalResponsibilityAnalysisService;
import com.chikere.futureai.responsibility.professional.analysis.ResponsibilityAnalysisFailedException;
import com.chikere.futureai.responsibility.professional.analysis.ResponsibilityAnalysisUnavailableException;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalResponsibilityGuidanceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The live smoke dashboard and the prefill routes. A prefill renders the journey's NORMAL form template with its
 * NORMAL form DTO; the visitor reviews it and submits it to the journey's normal POST endpoint, with CSRF, Bean
 * Validation and form mapping unchanged. Nothing here submits an assessment. The trace route serves one
 * assessment's live-smoke trace file.
 */
@Controller
@Profile(ApplicationProfiles.LIVE_SMOKE)
@ConditionalOnProperty(prefix = "app.live-smoke", name = "enabled", havingValue = "true")
public class LiveSmokeController {

    private final LiveSmokeCaseCatalog catalog;
    private final LiveSmokeProviderStatus status;
    private final LiveSmokeTraceFiles traceFiles;
    private final LiveSmokeRuns runs;
    private final ProfessionalResponsibilityAnalysisService responsibilityAnalysis;
    private final ProfessionalResponsibilityGuidanceService responsibilityGuidance;
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public LiveSmokeController(LiveSmokeCaseCatalog catalog, LiveSmokeProviderStatus status,
            LiveSmokeTraceFiles traceFiles, LiveSmokeRuns runs,
            ProfessionalResponsibilityAnalysisService responsibilityAnalysis,
            ProfessionalResponsibilityGuidanceService responsibilityGuidance) {
        this.catalog = catalog;
        this.status = status;
        this.traceFiles = traceFiles;
        this.runs = runs;
        this.responsibilityAnalysis = responsibilityAnalysis;
        this.responsibilityGuidance = responsibilityGuidance;
    }

    /**
     * Generates the deterministic paid guidance of one Professional assessment (and its responsibility analysis if
     * it does not exist yet), or reuses the stored guidance. Entitlement is enforced by the service.
     */
    @PostMapping("/dev/live-smoke/professional/{assessmentId}/responsibility-guidance")
    public ResponseEntity<String> generateResponsibilityGuidance(@PathVariable String assessmentId) {
        UUID id = uuid(assessmentId);
        try {
            responsibilityGuidance.generateOrGet(id);
        }
        catch (ResponsibilityAnalysisUnavailableException unavailable) {
            return ResponseEntity.status(HttpStatus.CONFLICT).contentType(MediaType.TEXT_PLAIN)
                    .body(unavailable.reason() + ": " + unavailable.getMessage());
        }
        catch (ResponsibilityAnalysisFailedException failed) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).contentType(MediaType.TEXT_PLAIN)
                    .body("FAILED at " + failed.stage() + "; nothing was stored and the analysis can be retried");
        }
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .header(HttpHeaders.LOCATION, "/dev/live-smoke/professional/" + id + "/responsibility-guidance").build();
    }

    /** The stored guidance, read-only: never runs the policy or any provider. */
    @GetMapping("/dev/live-smoke/professional/{assessmentId}/responsibility-guidance")
    public ResponseEntity<String> responsibilityGuidance(@PathVariable String assessmentId) throws Exception {
        var snapshot = responsibilityGuidance.find(uuid(assessmentId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(snapshot));
    }

    /**
     * Generates the paid responsibility analysis of one Professional assessment, or reuses the stored snapshot. The
     * service enforces the ACTIVE entitlement (complimentary under this profile), so this route grants nothing.
     */
    @PostMapping("/dev/live-smoke/professional/{assessmentId}/responsibility-analysis")
    public ResponseEntity<String> generateResponsibilityAnalysis(@PathVariable String assessmentId) {
        UUID id = uuid(assessmentId);
        try {
            responsibilityAnalysis.generateOrGet(id);
        }
        catch (ResponsibilityAnalysisUnavailableException unavailable) {
            return ResponseEntity.status(HttpStatus.CONFLICT).contentType(MediaType.TEXT_PLAIN)
                    .body(unavailable.reason() + ": " + unavailable.getMessage());
        }
        catch (ResponsibilityAnalysisFailedException failed) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).contentType(MediaType.TEXT_PLAIN)
                    .body("FAILED at " + failed.stage() + "; nothing was stored and the analysis can be retried");
        }
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .header(HttpHeaders.LOCATION, "/dev/live-smoke/professional/" + id + "/responsibility-analysis").build();
    }

    /** The stored snapshot, read-only: this never calls a provider or regenerates anything. */
    @GetMapping("/dev/live-smoke/professional/{assessmentId}/responsibility-analysis")
    public ResponseEntity<String> responsibilityAnalysis(@PathVariable String assessmentId) throws Exception {
        var snapshot = responsibilityAnalysis.find(uuid(assessmentId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(snapshot));
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        }
        catch (IllegalArgumentException notAnIdentifier) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping("/dev/live-smoke")
    public String dashboard(Model model) {
        Map<LiveSmokeJourney, Object> cases = new LinkedHashMap<>();
        Arrays.stream(LiveSmokeJourney.values()).forEach(journey -> cases.put(journey, catalog.forJourney(journey)));
        model.addAttribute("casesByJourney", cases);
        model.addAttribute("journeys", LiveSmokeJourney.values());
        model.addAttribute("status", status);
        return "dev/live-smoke";
    }

    /**
     * A custom assessment: the journey's normal blank form, exactly as its own GET route renders it. Submitting it
     * runs the normal workflow and, under this profile, the same entitlement and trace behaviour as a saved case.
     */
    @GetMapping("/dev/live-smoke/{journey}/new")
    public String custom(@PathVariable String journey, Model model) {
        LiveSmokeJourney value = LiveSmokeJourney.fromSlug(journey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("form", value.newForm());
        return value.formView();
    }

    /**
     * The trace file of exactly one assessment. The identifier must parse as a UUID and the file is resolved only
     * from it inside the trace directory, so no other path can be named.
     */
    @GetMapping("/dev/live-smoke/traces/{assessmentId}")
    public ResponseEntity<Resource> trace(@PathVariable String assessmentId) {
        UUID id;
        try {
            id = UUID.fromString(assessmentId);
        }
        catch (IllegalArgumentException notAnIdentifier) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        var file = traceFiles.fileFor(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String name = runs.caseFor(id).map(LiveSmokeCase::id).orElse("custom") + "-" + id + ".jsonl";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/x-ndjson"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(new FileSystemResource(file));
    }

    @GetMapping("/dev/live-smoke/{journey}/{caseId}")
    public String prefill(@PathVariable String journey, @PathVariable String caseId, Model model) {
        var smokeCase = LiveSmokeJourney.fromSlug(journey).flatMap(value -> catalog.find(value, caseId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("form", catalog.form(smokeCase));
        model.addAttribute("liveSmokeCase", smokeCase);
        return smokeCase.journey().formView();
    }
}
