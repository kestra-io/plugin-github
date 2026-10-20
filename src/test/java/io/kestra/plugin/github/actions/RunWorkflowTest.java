package io.kestra.plugin.github.actions;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.github.AbstractGithubClientTest;
import io.kestra.plugin.github.MockController;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
public class RunWorkflowTest extends AbstractGithubClientTest {
    @Inject
    private RunContextFactory runContextFactory;

    private RunWorkflow.RunWorkflowBuilder<?, ?> builder(boolean wait, boolean failOnConclusion) {
        return RunWorkflow.builder()
            .oauthToken(Property.ofValue("test-token"))
            .endpoint(Property.ofValue(embeddedServer.getURI().toString()))
            .repository(Property.ofValue("kestra-io/mock-kestra"))
            .workflowId(Property.ofValue("105842276")) // https://api.github.com/repos/kestra-io/plugin-github/actions/workflows/105842276
            .ref(Property.ofValue("master"))
            .wait(Property.ofValue(wait))
            .failOnConclusion(Property.ofValue(failOnConclusion));
    }

    private RunWorkflow task(boolean wait, boolean failOnConclusion) {
        return builder(wait, failOnConclusion).build();
    }

    @Test
    void testRunWorkflow() throws Exception {
        MockController.runStatus = "queued";

        var output = task(false, true).run(runContextFactory.of());

        assertThat(MockController.data).contains("\"ref\":\"master\"").contains("\"return_run_details\":true");
        assertThat(output.getRunId()).isEqualTo(101L);
        assertThat(output.getHtmlUrl()).isEqualTo("https://github.com/kestra-io/mock-kestra/actions/runs/101");
        assertThat(output.getStatus()).isEqualTo("queued");
        assertThat(output.getConclusion()).isNull();
    }

    @Test
    void testRunWorkflowFallsBackToLookupWhenNoRunDetailsAreReturned() throws Exception {
        MockController.dispatchReturnsRunDetails = false;

        var output = task(false, true).run(runContextFactory.of());

        assertThat(output.getRunId()).isEqualTo(101L);
        assertThat(output.getHtmlUrl()).isEqualTo("https://github.com/kestra-io/mock-kestra/actions/runs/101");
        assertThat(output.getStatus()).isEqualTo("queued");
        assertThat(output.getConclusion()).isNull();
    }

    @Test
    void testDispatchFailureIsReported() {
        MockController.dispatchFails = true;

        var exception = assertThrows(IllegalStateException.class, () -> task(false, true).run(runContextFactory.of()));

        assertThat(exception.getMessage()).contains("HTTP 400");
    }

    @Test
    void testWaitSuccess() throws Exception {
        var output = task(true, true).run(runContextFactory.of());

        assertThat(output.getRunId()).isEqualTo(101L);
        assertThat(output.getStatus()).isEqualTo("completed");
        assertThat(output.getConclusion()).isEqualTo("success");
    }

    @Test
    void testWaitFailureThrows() {
        MockController.runConclusion = "failure";

        var exception = assertThrows(IllegalStateException.class, () -> task(true, true).run(runContextFactory.of()));

        assertThat(exception.getMessage()).contains("failure");
    }

    @Test
    void testWaitFailureDoesNotThrowWhenDisabled() throws Exception {
        MockController.runConclusion = "failure";

        var output = task(true, false).run(runContextFactory.of());

        assertThat(output.getConclusion()).isEqualTo("failure");
    }

    @Test
    void testWaitTimeoutCancelsTheRunWhenEnabled() {
        MockController.runStatus = "in_progress";
        var task = builder(true, true)
            .waitTimeout(Property.ofValue(Duration.ofSeconds(1)))
            .pollInterval(Property.ofValue(Duration.ofMillis(200)))
            .cancelOnTimeout(Property.ofValue(true))
            .build();

        var exception = assertThrows(IllegalStateException.class, () -> task.run(runContextFactory.of()));

        assertThat(exception.getMessage()).contains("did not complete");
        assertThat(MockController.runCancelled).isTrue();
    }

    @Test
    void testWaitTimeoutDoesNotCancelTheRunByDefault() {
        MockController.runStatus = "in_progress";
        var task = builder(true, true)
            .waitTimeout(Property.ofValue(Duration.ofSeconds(1)))
            .pollInterval(Property.ofValue(Duration.ofMillis(200)))
            .build();

        var exception = assertThrows(IllegalStateException.class, () -> task.run(runContextFactory.of()));

        assertThat(exception.getMessage()).contains("did not complete");
        assertThat(MockController.runCancelled).isFalse();
    }

    @Test
    void testKillCancelsTheRun() throws Exception {
        MockController.runStatus = "in_progress";
        var task = builder(true, true)
            .pollInterval(Property.ofValue(Duration.ofMillis(200)))
            .build();
        var future = CompletableFuture.supplyAsync(() -> {
            try {
                return task.run(runContextFactory.of());
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        });

        try {
            task.kill();

            var deadline = Instant.now().plusSeconds(15);
            while (!MockController.runCancelled && Instant.now().isBefore(deadline)) {
                Thread.sleep(100);
            }

            assertThat(MockController.runCancelled).isTrue();
        } finally {
            MockController.runStatus = "completed";
            future.handle((output, error) -> null).get(15, TimeUnit.SECONDS);
        }
    }
}
