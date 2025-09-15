package io.kestra.plugin.github.code;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.FileSerde;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.plugin.github.AbstractGithubTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
@DisabledIf(
    value = "isTokenMissing",
    disabledReason = "Disabled: GITHUB_TOKEN not set"
)
public class SearchTest extends AbstractGithubTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Inject
    private StorageInterface storageInterface;

    @Test
    void testQuery() throws Exception {
        RunContext runContext = runContextFactory.of();

        Search task = Search.builder()
            .oauthToken(Property.ofValue(getToken()))
            .query(Property.ofValue("run in:file language:java repo:kestra-io/plugin-github"))
            .build();

        Search.Output run = task.run(runContext);

        assertThat(run.getUri(), is(notNullValue()));

        List<Map<String, Object>> result = getResult(run);

        assertThat(result.size(), greaterThanOrEqualTo(1));

        assertThat(result.getFirst().get("repository_name"), is("jquery"));
    }

    @Test
    void testParameters() throws Exception {
        RunContext runContext = runContextFactory.of();

        Search task = Search.builder()
            .oauthToken(Property.ofValue(""))
            .query(Property.ofValue("run"))
            .in(Property.ofValue("file"))
            .language(Property.ofValue("java"))
            .repository(Property.ofValue("kestra-io/plugin-github"))
            .build();

        Search.Output run = task.run(runContext);

        assertThat(run.getUri(), is(notNullValue()));

        List<Map<String, Object>> result = getResult(run);

        assertThat(result.size(), greaterThanOrEqualTo(1));

        assertThat(result.getFirst().get("repository_name"), is("jquery"));
    }

    private List<Map<String, Object>> getResult(Search.Output run) throws IOException {
        BufferedReader inputStream = new BufferedReader(new InputStreamReader(storageInterface.get(TenantService.MAIN_TENANT, null, run.getUri())));
        List<Map<String, Object>> result = new ArrayList<>();
        FileSerde.reader(inputStream, r -> result.add((Map<String, Object>) r));
        return result;
    }
}
