package io.kestra.plugin.github.actions;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.github.AbstractGithubClientTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
public class ListRunsTest extends AbstractGithubClientTest {
    @Inject
    private RunContextFactory runContextFactory;

    private ListRuns.ListRunsBuilder<?, ?> task() {
        return ListRuns.builder()
            .oauthToken(Property.ofValue(""))
            .endpoint(Property.ofValue(embeddedServer.getURI().toString()))
            .repository(Property.ofValue("kestra-io/mock-kestra"));
    }

    @Test
    void testListRuns() throws Exception {
        var output = task().build().run(runContextFactory.of());

        assertThat(output.getSize()).isGreaterThanOrEqualTo(1);
        assertThat(output.getRuns()).hasSize(output.getSize());

        var last = output.getRuns().get(output.getRuns().size() - 1);
        assertThat(last).containsEntry("runId", 100L);
        assertThat(last).containsEntry("status", "completed");
        assertThat(last).containsEntry("conclusion", "success");
        assertThat(last).containsEntry("headBranch", "master");
    }

    @Test
    void testListRunsWithFiltersAndLimit() throws Exception {
        var output = task()
            .branch(Property.ofValue("master"))
            .status(Property.ofValue("completed"))
            .limit(Property.ofValue(1))
            .build()
            .run(runContextFactory.of());

        assertThat(output.getSize()).isEqualTo(1);
        assertThat(output.getRuns()).hasSize(1);
    }
}