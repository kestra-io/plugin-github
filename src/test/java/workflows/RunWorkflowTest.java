package workflows;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.github.workflows.RunWorkflow;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@KestraTest
@Disabled("disabled for ci/cd, as there unit tests requires secret (oauth) token")
public class RunWorkflowTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void testRunWorkflow() throws Exception {
        var runContext = runContextFactory.of();

        var runWorkflowTask = RunWorkflow.builder()
            .oauthToken(Property.of(""))
            .repository(Property.of("kestra-io/plugin-github"))
            .workflowId(Property.of("105842276")) // https://api.github.com/repos/kestra-io/plugin-github/actions/workflows/105842276
            .ref(Property.of("master"))
            .build();

        runWorkflowTask.run(runContext);
    }
}
