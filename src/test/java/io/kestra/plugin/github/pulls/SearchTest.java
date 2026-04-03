package io.kestra.plugin.github.pulls;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.FileSerde;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.plugin.github.AbstractGithubClientTest;
import io.kestra.plugin.github.AbstractGithubSearchTask;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
public class SearchTest extends AbstractGithubClientTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Inject
    private StorageInterface storageInterface;

    @Test
    void testQuery() throws Exception {
        RunContext runContext = runContextFactory.of();

        Search task = Search.builder()
            .oauthToken(Property.ofValue("oauth-token"))
            .endpoint(Property.ofValue(embeddedServer.getURI().toString()))
            .query(Property.ofValue("repo:kestra-io/plugin-github is:closed"))
            .sort(Property.ofValue(Search.Sort.UPDATED))
            .build();

        AbstractGithubSearchTask.Output run = task.run(runContext);

        assertThat(run.getUri(), is(notNullValue()));

        List<Map<String, Object>> result = getResult(run);

        assertThat(result.size(), greaterThanOrEqualTo(1));

        assertThat(result.getFirst().get("state"), is("CLOSED"));
    }

    @Test
    void testParameters() throws Exception {
        RunContext runContext = runContextFactory.of();

        Search task = Search.builder()
            .oauthToken(Property.ofValue("oauth-token"))
            .endpoint(Property.ofValue(embeddedServer.getURI().toString()))
            .query(Property.ofValue("repo:kestra-io/plugin-github"))
            .open(Property.ofValue(Boolean.FALSE))
            .sort(Property.ofValue(Search.Sort.UPDATED))
            .build();

        AbstractGithubSearchTask.Output run = task.run(runContext);

        assertThat(run.getUri(), is(notNullValue()));

        List<Map<String, Object>> result = getResult(run);

        assertThat(result.size(), greaterThanOrEqualTo(1));

        assertThat(result.getFirst().get("state"), is("CLOSED"));
    }

    @Test
    void testReviewRequested() throws Exception {
        RunContext runContext = runContextFactory.of();

        Search task = Search.builder()
            .oauthToken(Property.ofValue("oauth-token"))
            .endpoint(Property.ofValue(embeddedServer.getURI().toString()))
            .query(Property.ofValue("repo:kestra-io/plugin-github"))
            .reviewRequested(Property.ofValue("copilot"))
            .closed(Property.ofValue(Boolean.TRUE))
            .sort(Property.ofValue(Search.Sort.UPDATED))
            .build();

        AbstractGithubSearchTask.Output run = task.run(runContext);

        assertThat(run.getUri(), is(notNullValue()));

        List<Map<String, Object>> result = getResult(run);

        assertThat(result.size(), greaterThanOrEqualTo(1));

        assertThat(result.getFirst().get("state"), is("CLOSED"));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getResult(AbstractGithubSearchTask.Output run) throws IOException {
        BufferedReader inputStream = new BufferedReader(new InputStreamReader(storageInterface.get(TenantService.MAIN_TENANT, null, run.getUri())));
        List<Map<String, Object>> result = new ArrayList<>();
        FileSerde.reader(inputStream, r -> result.add((Map<String, Object>) r));
        return result;
    }
}
