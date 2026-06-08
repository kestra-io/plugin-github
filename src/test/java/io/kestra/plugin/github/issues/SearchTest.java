package io.kestra.plugin.github.issues;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.FileSerde;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.tenant.TenantService;
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
public class SearchTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Inject
    private StorageInterface storageInterface;

    @Test
    void testQuery() throws Exception {
        RunContext runContext = runContextFactory.of();

        Search task = Search.builder()
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
            .query(Property.ofValue("repo:kestra-io/plugin-github"))
            .open(Property.ofValue(Boolean.TRUE))
            .closed(Property.ofValue(Boolean.TRUE))
            .sort(Property.ofValue(Search.Sort.UPDATED))
            .build();

        AbstractGithubSearchTask.Output run = task.run(runContext);

        assertThat(run.getUri(), is(notNullValue()));

        List<Map<String, Object>> result = getResult(run);

        assertThat(result.size(), greaterThanOrEqualTo(1));
    }

    @Test
    void testQueryAndRepository() throws Exception {
        RunContext runContext = runContextFactory.of();

        var task = Search.builder()
            .repository(Property.ofValue("kestra-io/plugin-github"))
            .sort(Property.ofValue(Search.Sort.UPDATED))
            .build();

        AbstractGithubSearchTask.Output run = task.run(runContext);

        assertThat(run.getUri(), is(notNullValue()));

        List<Map<String, Object>> result = getResult(run);

        assertThat(result.size(), greaterThanOrEqualTo(1));

        Map<String, Object> first = result.getFirst();
        assertThat(first, hasKey("repository_name"));
        assertThat(first, hasKey("state"));

        assertThat((String) first.get("url"), containsString("plugin-github"));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getResult(AbstractGithubSearchTask.Output run) throws IOException {
        try (var inputStream = new BufferedInputStream(storageInterface.get(TenantService.MAIN_TENANT, null, run.getUri()))) {
            var iterator = JacksonMapper.ofIon().readerFor(Object.class).readValues(inputStream);
            return (List) FileSerde.readAll(iterator).collectList().block();
        }
    }
}
