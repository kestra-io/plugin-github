package io.kestra.plugin.github.actions;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.common.FetchType;
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
        var output = task()
            .fetchType(Property.ofValue(FetchType.FETCH))
            .build()
            .run(runContextFactory.of());

        assertThat(output.getSize()).isGreaterThanOrEqualTo(1);
        assertThat(output.getRows()).hasSize(output.getSize());

        var last = output.getRows().get(output.getRows().size() - 1);
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
            .fetchType(Property.ofValue(FetchType.FETCH))
            .build()
            .run(runContextFactory.of());

        assertThat(output.getSize()).isEqualTo(1);
        assertThat(output.getRows()).hasSize(1);
    }

    @Test
    void testFetchOne() throws Exception {
        var output = task()
            .fetchType(Property.ofValue(FetchType.FETCH_ONE))
            .build()
            .run(runContextFactory.of());

        assertThat(output.getSize()).isEqualTo(1);
        assertThat(output.getRow()).containsKey("runId");
        assertThat(output.getRows()).isNull();
    }

    @Test
    void testStoreIsTheDefault() throws Exception {
        var output = task().build().run(runContextFactory.of());

        assertThat(output.getUri()).isNotNull();
        assertThat(output.getSize()).isGreaterThanOrEqualTo(1);
        assertThat(output.getRows()).isNull();
    }

    @Test
    void testInvalidLimitIsRejected() {
        var task = task().limit(Property.ofValue(500)).build();

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> task.run(runContextFactory.of()));
    }
}