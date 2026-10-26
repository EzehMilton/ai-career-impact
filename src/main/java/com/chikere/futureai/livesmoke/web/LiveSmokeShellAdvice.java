package com.chikere.futureai.livesmoke.web;

import com.chikere.futureai.configuration.ApplicationProfiles;
import com.chikere.futureai.livesmoke.LiveSmokeRuns;
import com.chikere.futureai.livesmoke.LiveSmokeTraceFiles;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Supplies the live-smoke banner flag and the run registry to every page. This advice exists only under the
 * live-smoke profile, so in every other profile the attributes are absent and nothing extra renders.
 */
@ControllerAdvice
@Profile(ApplicationProfiles.LIVE_SMOKE)
@ConditionalOnProperty(prefix = "app.live-smoke", name = "enabled", havingValue = "true")
public class LiveSmokeShellAdvice {

    private final LiveSmokeRuns runs;
    private final LiveSmokeTraceFiles traceFiles;

    public LiveSmokeShellAdvice(LiveSmokeRuns runs, LiveSmokeTraceFiles traceFiles) {
        this.runs = runs;
        this.traceFiles = traceFiles;
    }

    @ModelAttribute("liveSmokeMode")
    public boolean liveSmokeMode() {
        return true;
    }

    @ModelAttribute("liveSmokeRuns")
    public LiveSmokeRuns liveSmokeRuns() {
        return runs;
    }

    @ModelAttribute("liveSmokeTraces")
    public LiveSmokeTraceFiles liveSmokeTraces() {
        return traceFiles;
    }
}
