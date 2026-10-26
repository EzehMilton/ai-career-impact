package com.chikere.futureai.livesmoke.web;

import com.chikere.futureai.configuration.ApplicationProfiles;
import com.chikere.futureai.livesmoke.LiveSmokeCase;
import com.chikere.futureai.livesmoke.LiveSmokeCaseCatalog;
import com.chikere.futureai.livesmoke.LiveSmokeJourney;
import com.chikere.futureai.livesmoke.LiveSmokeProviderStatus;
import com.chikere.futureai.livesmoke.LiveSmokeRuns;
import com.chikere.futureai.livesmoke.LiveSmokeTraceFiles;
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

    public LiveSmokeController(LiveSmokeCaseCatalog catalog, LiveSmokeProviderStatus status,
            LiveSmokeTraceFiles traceFiles, LiveSmokeRuns runs) {
        this.catalog = catalog;
        this.status = status;
        this.traceFiles = traceFiles;
        this.runs = runs;
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
