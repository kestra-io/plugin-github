package io.kestra.plugin.github.users;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.FileSerde;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.plugin.github.AbstractGithubClientTest;
import io.kestra.plugin.github.AbstractGithubSearchTask;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.BufferedInputStream;
import java.io.IOException;
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
            .oauthToken(Property.ofValue(""))
            .endpoint(Property.ofValue(embeddedServer.getURI().toString()))
            .query(Property.ofValue("kestra-io in:login language:java"))
            .build();

        AbstractGithubSearchTask.Output run = task.run(runContext);

        assertThat(run.getUri(), is(notNullValue()));

        List<Map<String, Object>> result = getResult(run);

        assertThat(result.size(), equalTo(1));

        assertThat(result.getFirst().get("username"), is("kestra-io"));
    }

    @Test
    void testParameters() throws Exception {
        RunContext runContext = runContextFactory.of();

        Search task = Search.builder()
            .oauthToken(Property.ofValue(""))
            .endpoint(Property.ofValue(embeddedServer.getURI().toString()))
            .query(Property.ofValue("kestra-io"))
            .in(Property.ofValue("login"))
            .language(Property.ofValue("java"))
            .build();

        AbstractGithubSearchTask.Output run = task.run(runContext);

        assertThat(run.getUri(), is(notNullValue()));

        List<Map<String, Object>> result = getResult(run);

        assertThat(result.size(), equalTo(1));

        assertThat(result.getFirst().get("username"), is("kestra-io"));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getResult(AbstractGithubSearchTask.Output run) throws IOException {
        try (var inputStream = new BufferedInputStream(storageInterface.get(TenantService.MAIN_TENANT, null, run.getUri()))) {
            var iterator = JacksonMapper.ofIon().readerFor(Object.class).readValues(inputStream);
            return (List) FileSerde.readAll(iterator).collectList().block();
        }
    }
}
