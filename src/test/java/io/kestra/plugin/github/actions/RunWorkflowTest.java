package io.kestra.plugin.github.actions;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@KestraTest
@EnabledIfEnvironmentVariable(named = "GITHUB_TOKEN", matches = ".+")
@EnabledIfEnvironmentVariable(named = "GITHUB_REF_NAME", matches = "master")
public class RunWorkflowTest {
    private static final String GITHUB_OAUTH_TOKEN = System.getenv("GITHUB_TOKEN");

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void testRunWorkflow() throws Exception {
        var runContext = runContextFactory.of();

        var runWorkflowTask = RunWorkflow.builder()
            .oauthToken(Property.ofValue(GITHUB_OAUTH_TOKEN))
            .repository(Property.ofValue("kestra-io/plugin-github"))
            .workflowId(Property.ofValue("105842276")) // https://api.github.com/repos/kestra-io/plugin-github/actions/workflows/105842276
            .ref(Property.ofValue("master"))
            .build();

        runWorkflowTask.run(runContext);
    }
}
