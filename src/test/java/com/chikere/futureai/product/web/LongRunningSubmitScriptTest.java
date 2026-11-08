package com.chikere.futureai.product.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Runs the long-running submission behaviour tests (src/test/js) with Node's built-in test
 * runner, so they take part in {@code ./mvnw verify} without adding a JavaScript toolchain to the
 * build. They are skipped, visibly, on a machine without {@code node} on the PATH.
 */
class LongRunningSubmitScriptTest {

    private static final Path SPEC = Path.of("src", "test", "js", "long-running-submit.test.js");

    @Test
    void aValidSubmissionShowsTheLoadingStateOnceAndARestoredPageIsUsableAgain() throws Exception {
        assumeTrue(nodeIsAvailable(), "node is not on the PATH; skipping the long-running submission script tests");
        assertThat(SPEC).exists();

        Process node = new ProcessBuilder("node", "--test", SPEC.toString()).redirectErrorStream(true).start();
        boolean finished = node.waitFor(60, TimeUnit.SECONDS);
        String output = new String(node.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!finished) node.destroyForcibly();

        assertThat(finished).as("node test run finished").isTrue();
        assertThat(node.exitValue()).as(output).isZero();
        assertThat(output).contains("# fail 0").doesNotContain("# pass 0");
    }

    private static boolean nodeIsAvailable() {
        try {
            Process version = new ProcessBuilder("node", "--version").redirectErrorStream(true).start();
            return version.waitFor(10, TimeUnit.SECONDS) && version.exitValue() == 0;
        }
        catch (IOException unavailable) {
            return false;
        }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
