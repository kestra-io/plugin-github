package io.kestra.plugin.github.actions;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.github.AbstractGithubClientTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
public class GetRunTest extends AbstractGithubClientTest {
    @Inject
    private RunContextFactory runContextFactory;

    private GetRun.GetRunBuilder<?, ?> task() {
        return GetRun.builder()
            .oauthToken(Property.ofValue(""))
            .endpoint(Property.ofValue(embeddedServer.getURI().toString()))
            .repository(Property.ofValue("kestra-io/mock-kestra"))
            .runId(Property.ofValue(101L));
    }

    @Test
    void testGetRun() throws Exception {
        var output = task().build().run(runContextFactory.of());

        assertThat(output.getRunId()).isEqualTo(101L);
        assertThat(output.getRunNumber()).isEqualTo(101L);
        assertThat(output.getStatus()).isEqualTo("completed");
        assertThat(output.getConclusion()).isEqualTo("success");
        assertThat(output.getHeadSha()).isEqualTo("abc123");
        assertThat(output.getHtmlUrl()).isEqualTo("https://github.com/kestra-io/mock-kestra/actions/runs/101");
        assertThat(output.getCreatedAt()).isNotNull();
        assertThat(output.getUpdatedAt()).isNotNull();
        assertThat(output.getJobs()).isNull();
    }

    @Test
    void testGetRunWithJobs() throws Exception {
        var output = task().includeJobs(Property.ofValue(true)).build().run(runContextFactory.of());

        assertThat(output.getJobs()).hasSize(2);
        assertThat(output.getJobs().get(0)).containsEntry("name", "build").containsEntry("conclusion", "success");
        assertThat(output.getJobs().get(1)).containsEntry("name", "test").containsEntry("conclusion", "failure");
    }
}