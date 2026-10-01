package com.chikere.futureai.product.web;

import com.chikere.futureai.product.application.DevelopmentModeStatus;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * Supplies the handful of model attributes the shared page shell needs on every page.
 *
 * <p>Keeping these here means no journey controller has to know about product chrome.</p>
 *
 * <p>Both dependencies are resolved through {@link ObjectProvider} so a sliced web test that loads
 * only one controller still works. The fallback for development mode is deliberately
 * {@link DevelopmentModeStatus#inactive()}: if the shell configuration is absent for any reason,
 * the safe assumption is that this is not a development environment and no development chrome
 * should be rendered.</p>
 */
@ControllerAdvice
public class ProductShellAdvice {

    private final DevelopmentModeStatus developmentModeStatus;
    private final Clock clock;

    public ProductShellAdvice(ObjectProvider<DevelopmentModeStatus> developmentModeStatus,
            ObjectProvider<Clock> clock) {
        this.developmentModeStatus = Objects.requireNonNullElseGet(
                developmentModeStatus.getIfAvailable(), DevelopmentModeStatus::inactive);
        this.clock = Objects.requireNonNullElseGet(clock.getIfAvailable(), Clock::systemUTC);
    }

    @ModelAttribute("developmentMode")
    public boolean developmentMode() {
        return developmentModeStatus.active();
    }

    @ModelAttribute("developmentFixtures")
    public String developmentFixtures() {
        return developmentModeStatus.fixtureSummary().orElse(null);
    }

    @ModelAttribute("currentYear")
    public int currentYear() {
        return ZonedDateTime.now(clock.withZone(ZoneId.of("Europe/London"))).getYear();
    }
}
