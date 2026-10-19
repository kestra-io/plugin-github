package io.kestra.plugin.github.actions;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.github.AbstractGithubClientTest;
import io.kestra.plugin.github.MockController;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
public class RunWorkflowTest extends AbstractGithubClientTest {
    @Inject
    private RunContextFactory runContextFactory;

    private RunWorkflow task(boolean wait, boolean failOnConclusion) {
        return RunWorkflow.builder()
            .oauthToken(Property.ofValue(""))
            .endpoint(Property.ofValue(embeddedServer.getURI().toString()))
            .repository(Property.ofValue("kestra-io/mock-kestra"))
            .workflowId(Property.ofValue("105842276")) // https://api.github.com/repos/kestra-io/plugin-github/actions/workflows/105842276
            .ref(Property.ofValue("master"))
            .wait(Property.ofValue(wait))
            .failOnConclusion(Property.ofValue(failOnConclusion))
            .build();
    }

    @Test
    void testRunWorkflow() throws Exception {
        var output = task(false, true).run(runContextFactory.of());

        assertThat(MockController.data).isEqualTo("{\"ref\":\"master\"}");
        assertThat(output.getRunId()).isEqualTo(101L);
        assertThat(output.getHtmlUrl()).isEqualTo("https://github.com/kestra-io/mock-kestra/actions/runs/101");
        assertThat(output.getStatus()).isEqualTo("queued");
        assertThat(output.getConclusion()).isNull();
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
        try {
            var exception = assertThrows(IllegalStateException.class, () -> task(true, true).run(runContextFactory.of()));
            assertThat(exception.getMessage()).contains("failure");
        } finally {
            MockController.runConclusion = "success";
        }
    }

    @Test
    void testWaitFailureDoesNotThrowWhenDisabled() throws Exception {
        MockController.runConclusion = "failure";
        try {
            var output = task(true, false).run(runContextFactory.of());
            assertThat(output.getConclusion()).isEqualTo("failure");
        } finally {
            MockController.runConclusion = "success";
        }
    }
}