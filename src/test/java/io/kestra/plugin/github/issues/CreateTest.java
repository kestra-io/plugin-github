package io.kestra.plugin.github.issues;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
@Disabled("disabled for ci/cd, as there unit tests requires secret (oauth) token")
public class CreateTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void run() throws Exception {
        RunContext runContext = runContextFactory.of();

        Create task = Create.builder()
            .oauthToken(Property.of(""))
            .repository(Property.of("kestra-io/plugin-github"))
            .title(Property.of("Test Kestra Github plugin"))
            .body(Property.of("This is a test for creating a new issue in repository by oauth token"))
            .labels(Property.of(List.of("kestra", "test")))
            .assignees(Property.of(List.of("iNikitaGricenko")))
            .build();

        Create.Output run = task.run(runContext);

        assertThat(run.getIssueUrl(), is(notNullValue()));
        assertThat(run.getIssueNumber(), is(notNullValue()));
    }
}
