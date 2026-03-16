package io.kestra.plugin.github.code;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.FileSerde;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.plugin.github.AbstractGithubClientTest;
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
        var runContext = runContextFactory.of();

        var task = Search.builder()
            .oauthToken(Property.ofValue("oauth-token"))
            .endpoint(Property.ofValue(embeddedServer.getURI().toString()))
            .query(Property.ofValue("run in:file language:java repo:kestra-io/plugin-github"))
            .build();

        Search.Output run = task.run(runContext);

        assertThat(run.getUri(), is(notNullValue()));

        List<Map<String, Object>> result = getResult(run);

        assertThat(result.size(), greaterThanOrEqualTo(1));
        assertThat(result.getFirst().get("repository_name"), is("plugin-github"));
    }

    @Test
    void testParameters() throws Exception {
        var runContext = runContextFactory.of();

        var task = Search.builder()
            .oauthToken(Property.ofValue("oauth-token"))
            .endpoint(Property.ofValue(embeddedServer.getURI().toString()))
            .query(Property.ofValue("run"))
            .in(Property.ofValue("file"))
            .language(Property.ofValue("java"))
            .repository(Property.ofValue("kestra-io/plugin-github"))
            .build();

        Search.Output run = task.run(runContext);

        assertThat(run.getUri(), is(notNullValue()));

        List<Map<String, Object>> result = getResult(run);

        assertThat(result.size(), greaterThanOrEqualTo(1));
        assertThat(result.getFirst().get("repository_name"), is("plugin-github"));
    }

    private List<Map<String, Object>> getResult(Search.Output run) throws IOException {
        BufferedReader inputStream = new BufferedReader(new InputStreamReader(storageInterface.get(TenantService.MAIN_TENANT, null, run.getUri())));
        List<Map<String, Object>> result = new ArrayList<>();
        FileSerde.reader(inputStream, r -> result.add((Map<String, Object>) r));
        return result;
    }
}
