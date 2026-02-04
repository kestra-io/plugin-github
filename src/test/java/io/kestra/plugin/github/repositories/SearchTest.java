package io.kestra.plugin.github.repositories;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.FileSerde;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.tenant.TenantService;
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
public class SearchTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Inject
    private StorageInterface storageInterface;

    @Test
    void testQuery() throws Exception {
        RunContext runContext = runContextFactory.of();

        Search task = Search.builder()
            .query(Property.ofValue("user:kestra-io language:java is:public"))
            .sort(Property.ofValue(Search.Sort.STARS))
            .build();

        Search.FileOutput run = task.run(runContext);

        assertThat(run.getUri(), is(notNullValue()));

        List<Map<String, Object>> result = getResult(run);

        assertThat(result.size(), greaterThanOrEqualTo(1));
    }

    @Test
    void testParameters() throws Exception {
        RunContext runContext = runContextFactory.of();

        Search task = Search.builder()
            .repository(Property.ofValue("kestra-io/plugin-github"))
            .language(Property.ofValue("java"))
            .visibility(Property.ofValue(Search.Visibility.PUBLIC))
            .sort(Property.ofValue(Search.Sort.STARS))
            .build();

        Search.FileOutput run = task.run(runContext);

        assertThat(run.getUri(), is(notNullValue()));

        List<Map<String, Object>> result = getResult(run);

        assertThat(result.size(), equalTo(1));

        assertThat(result.getFirst().get("name"), is("plugin-github"));
        assertThat(result.getFirst().get("full_name"), is("kestra-io/plugin-github"));
    }

    private List<Map<String, Object>> getResult(Search.FileOutput run) throws IOException {
        BufferedReader inputStream = new BufferedReader(new InputStreamReader(storageInterface.get(TenantService.MAIN_TENANT, null, run.getUri())));
        List<Map<String, Object>> result = new ArrayList<>();
        FileSerde.reader(inputStream, r -> {
            if (r instanceof List<?> list) {
                list.forEach(item -> result.add((Map<String, Object>) item));
            } else {
                result.add((Map<String, Object>) r);
            }
        });
        return result;
    }
}
