package io.kestra.plugin.github.issues;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.github.AbstractGithubClientTest;
import io.kestra.plugin.github.MockController;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest
public class CreateTest extends AbstractGithubClientTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void run() throws Exception {
        var runContext = runContextFactory.of();

        var task = Create.builder()
            .oauthToken(Property.ofValue(""))
            .endpoint(Property.ofValue(embeddedServer.getURI().toString()))
            .repository(Property.ofValue("kestra-io/mock-kestra"))
            .title(Property.ofValue("Test Kestra Github plugin"))
            .body(Property.ofValue("This is a test for creating a new issue in repository by oauth token"))
            .labels(Property.ofValue(List.of("kestra", "test")))
            .assignees(Property.ofValue(List.of("iNikitaGricenko")))
            .build();

        var output = task.run(runContext);

        assertThat(output.getIssueUrl()).isNotNull();
        assertThat(output.getIssueNumber()).isNotNull();
        assertThat(output.getIssueNumber()).isEqualTo(42);
    }

    @Test
    void runWithFieldValues() throws Exception {
        var runContext = runContextFactory.of();

        var task = Create.builder()
            .oauthToken(Property.ofValue("test-token"))
            .endpoint(Property.ofValue(embeddedServer.getURI().toString()))
            .repository(Property.ofValue("kestra-io/mock-kestra"))
            .title(Property.ofValue("Test Kestra Github plugin with field values"))
            .body(Property.ofValue("Issue with custom field values"))
            .fieldValues(Property.ofValue(Map.of("PVTF_lADOA", "high", "PVTF_lADOB", "2024-12-31")))
            .build();

        var output = task.run(runContext);

        assertThat(output.getIssueNumber()).isEqualTo(42);
        assertThat(MockController.data).contains("field_values");
        assertThat(MockController.data).contains("PVTF_lADOA");
        assertThat(MockController.headers).containsEntry("x-github-api-version", "2026-03-10");
        assertThat(MockController.headers.get("authorization")).startsWith("Bearer ");
    }

    @Test
    void runWithEmptyFieldValuesSkipsRestCall() throws Exception {
        var runContext = runContextFactory.of();

        var task = Create.builder()
            .oauthToken(Property.ofValue(""))
            .endpoint(Property.ofValue(embeddedServer.getURI().toString()))
            .repository(Property.ofValue("kestra-io/mock-kestra"))
            .title(Property.ofValue("Test Kestra Github plugin"))
            .body(Property.ofValue("Issue without field values"))
            .fieldValues(Property.ofValue(Map.of()))
            .build();

        var output = task.run(runContext);

        assertThat(output.getIssueNumber()).isEqualTo(42);
        // field-values endpoint was never called, so data stays as set by createIssue mock
        assertThat(MockController.data).doesNotContain("field_values");
    }

    @Test
    void runWithFieldValuesButNoTokenThrows() {
        var runContext = runContextFactory.of();

        var task = Create.builder()
            .endpoint(Property.ofValue(embeddedServer.getURI().toString()))
            .repository(Property.ofValue("kestra-io/mock-kestra"))
            .title(Property.ofValue("Test"))
            .body(Property.ofValue("Body"))
            .fieldValues(Property.ofValue(Map.of("PVTF_lADOA", "high")))
            .build();

        assertThatThrownBy(() -> task.run(runContext))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No GitHub token configured");
    }
}
