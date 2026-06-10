package io.kestra.plugin.github.projects;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.github.AbstractGithubClientTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest
@Execution(ExecutionMode.SAME_THREAD)
class ListTest extends AbstractGithubClientTest {

    @Inject
    private RunContextFactory runContextFactory;

    private io.kestra.plugin.github.projects.List.ListBuilder<?, ?> defaultBuilder() {
        return io.kestra.plugin.github.projects.List.builder()
            .oauthToken(Property.ofValue("test-token"))
            .endpoint(Property.ofValue(embeddedServer.getURI().toString()));
    }

    @Test
    void happyPathSinglePage() throws Exception {
        var runContext = runContextFactory.of();

        var task = defaultBuilder()
            .organization(Property.ofValue("kestra-io"))
            .projectNumber(Property.ofValue(1))
            .fetchType(Property.ofValue(FetchType.FETCH))
            .build();

        var output = task.run(runContext);

        // Draft item and PullRequest content node must be skipped; only the Issue remains
        assertThat(output.getSize()).isEqualTo(1);
        assertThat(output.getRows()).hasSize(1);

        var item = output.getRows().getFirst();
        assertThat(item.get("number")).isEqualTo(1234);
        assertThat(item.get("title")).isEqualTo("My task");
        assertThat(item.get("url")).isEqualTo("https://github.com/kestra-io/kestra/issues/1234");
        assertThat(item.get("repository")).isEqualTo("kestra-ee");
        assertThat(item.get("status")).isEqualTo("In Progress");
        assertThat(item.get("Owner")).isEqualTo("Plugins");
        assertThat(item.get("createdAt")).isEqualTo("2025-03-01T10:00:00Z");
        assertThat(item.get("closedAt")).isNull();

        assertThat(item.get("assignees")).asList().containsExactly("alice");
        assertThat(item.get("labels")).asList().containsExactly("area/plugin");
    }

    @Test
    void multiPageCursorPagination() throws Exception {
        var runContext = runContextFactory.of();

        var task = defaultBuilder()
            .organization(Property.ofValue("kestra-io"))
            .projectNumber(Property.ofValue(2))
            .fetchType(Property.ofValue(FetchType.FETCH))
            .build();

        var output = task.run(runContext);

        // Project 2 returns 2 pages with 1 issue each
        assertThat(output.getSize()).isEqualTo(2);
        assertThat(output.getRows()).hasSize(2);
        assertThat(output.getRows().get(0).get("number")).isEqualTo(1);
        assertThat(output.getRows().get(1).get("number")).isEqualTo(2);
    }

    @Test
    void fieldsFilter() throws Exception {
        var runContext = runContextFactory.of();

        var task = defaultBuilder()
            .organization(Property.ofValue("kestra-io"))
            .projectNumber(Property.ofValue(2))
            .fields(Property.ofValue(Map.of("Owner", "Team A")))
            .fetchType(Property.ofValue(FetchType.FETCH))
            .build();

        var output = task.run(runContext);

        assertThat(output.getSize()).isEqualTo(1);
        assertThat(output.getRows().getFirst().get("Owner")).isEqualTo("Team A");
    }

    @Test
    void statusFilter() throws Exception {
        var runContext = runContextFactory.of();

        var task = defaultBuilder()
            .organization(Property.ofValue("kestra-io"))
            .projectNumber(Property.ofValue(2))
            .status(Property.ofValue(java.util.List.of("Done")))
            .fetchType(Property.ofValue(FetchType.FETCH))
            .build();

        var output = task.run(runContext);

        assertThat(output.getSize()).isEqualTo(1);
        assertThat(output.getRows().getFirst().get("status")).isEqualTo("Done");
    }

    @Test
    void labelsFilter() throws Exception {
        var runContext = runContextFactory.of();

        var task = defaultBuilder()
            .organization(Property.ofValue("kestra-io"))
            .projectNumber(Property.ofValue(2))
            .labels(Property.ofValue(java.util.List.of("area/core")))
            .fetchType(Property.ofValue(FetchType.FETCH))
            .build();

        var output = task.run(runContext);

        assertThat(output.getSize()).isEqualTo(1);

        assertThat(output.getRows().getFirst().get("labels")).asList().contains("area/core");
    }

    @Test
    void emptyStatusFilterIsNoOp() throws Exception {
        var runContext = runContextFactory.of();

        var task = defaultBuilder()
            .organization(Property.ofValue("kestra-io"))
            .projectNumber(Property.ofValue(2))
            .status(Property.ofValue(java.util.List.of()))
            .fetchType(Property.ofValue(FetchType.FETCH))
            .build();

        var output = task.run(runContext);

        // Empty list = no filter; both pages combined = 2 items
        assertThat(output.getSize()).isEqualTo(2);
    }

    @Test
    void storeFetchType() throws Exception {
        var runContext = runContextFactory.of();

        var task = defaultBuilder()
            .organization(Property.ofValue("kestra-io"))
            .projectNumber(Property.ofValue(1))
            .fetchType(Property.ofValue(FetchType.STORE))
            .build();

        var output = task.run(runContext);

        assertThat(output.getUri()).isNotNull();
        assertThat(output.getRows()).isNull();
        assertThat(output.getSize()).isEqualTo(1);
    }

    @Test
    void missingOrganizationThrows() {
        var runContext = runContextFactory.of();

        var task = defaultBuilder()
            .organization(Property.ofValue("missing-org"))
            .projectNumber(Property.ofValue(1))
            .fetchType(Property.ofValue(FetchType.FETCH))
            .build();

        assertThatThrownBy(() -> task.run(runContext))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("missing-org");
    }

    @Test
    void missingProjectThrows() {
        var runContext = runContextFactory.of();

        var task = defaultBuilder()
            .organization(Property.ofValue("kestra-io"))
            .projectNumber(Property.ofValue(999))
            .fetchType(Property.ofValue(FetchType.FETCH))
            .build();

        assertThatThrownBy(() -> task.run(runContext))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("999");
    }

    @Test
    void forbiddenThrows() {
        var runContext = runContextFactory.of();

        var task = defaultBuilder()
            .organization(Property.ofValue("forbidden-org"))
            .projectNumber(Property.ofValue(1))
            .fetchType(Property.ofValue(FetchType.FETCH))
            .build();

        assertThatThrownBy(() -> task.run(runContext))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("read:project");
    }
}
