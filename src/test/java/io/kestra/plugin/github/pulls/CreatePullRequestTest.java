package io.kestra.plugin.github.pulls;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@KestraTest
@EnabledIfEnvironmentVariable(named = "GITHUB_TOKEN", matches = ".+")
public class CreatePullRequestTest {
    private static final String GITHUB_OAUTH_TOKEN = System.getenv("GITHUB_TOKEN");

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void run() throws Exception {
        RunContext runContext = runContextFactory.of();

        io.kestra.plugin.github.pulls.Create task = io.kestra.plugin.github.pulls.Create.builder()
            .oauthToken(Property.ofValue(GITHUB_OAUTH_TOKEN))
            .repository(Property.ofValue("kestra-io/plugin-github"))
            .sourceBranch(Property.ofValue("dev"))
            .targetBranch(Property.ofValue("test"))
            .title(Property.ofValue("Test Kestra Github plugin"))
            .body(Property.ofValue("This is a test for creating a new pull request in repository by oauth token"))
            .maintainerCanModify(Property.ofValue(true))
            .build();

        Create.Output run = task.run(runContext);

        assertThat(run.getIssueUrl(), is(notNullValue()));
        assertThat(run.getPullRequestUrl(), is(notNullValue()));
    }
}
