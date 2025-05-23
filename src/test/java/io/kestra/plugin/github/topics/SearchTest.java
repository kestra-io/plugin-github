package io.kestra.plugin.github.topics;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.FileSerde;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.tenant.TenantService;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Disabled;
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
@Disabled("disabled for ci/cd, as there unit tests requires secret (oauth) token")
public class SearchTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Inject
    private StorageInterface storageInterface;

    @Test
    void testQuery() throws Exception {
        RunContext runContext = runContextFactory.of();

        Search task = Search.builder()
            .oauthToken(Property.of(""))
            .query(Property.of("Spring Cloud is:not-curated repositories:>10"))
            .build();

        Search.Output run = task.run(runContext);

        assertThat(run.getUri(), is(notNullValue()));

        List<Map<String, Object>> result = getResult(run);

        assertThat(result.size(), greaterThanOrEqualTo(1));

        assertThat(result.getFirst().get("name"), is("spring-cloud"));
    }

    @Test
    void testParameters() throws Exception {
        RunContext runContext = runContextFactory.of();

        Search task = Search.builder()
            .query(Property.of("Spring Cloud"))
            .is(Property.of(Search.Is.NOT_CURATED))
            .repositories(Property.of(">10"))
            .build();

        Search.Output run = task.run(runContext);

        assertThat(run.getUri(), is(notNullValue()));

        List<Map<String, Object>> result = getResult(run);

        assertThat(result.size(), greaterThanOrEqualTo(1));

        assertThat(result.getFirst().get("name"), is("spring-cloud"));
    }

    private List<Map<String, Object>> getResult(Search.Output run) throws IOException {
        BufferedReader inputStream = new BufferedReader(new InputStreamReader(storageInterface.get(TenantService.MAIN_TENANT, null, run.getUri())));
        List<Map<String, Object>> result = new ArrayList<>();
        FileSerde.reader(inputStream, r -> result.add((Map<String, Object>) r));
        return result;
    }
}
