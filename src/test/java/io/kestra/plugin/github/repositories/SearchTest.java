package io.kestra.plugin.github.repositories;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@KestraTest
public class SearchTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void testQuery() throws Exception {
        RunContext runContext = runContextFactory.of();

        Search task = Search.builder()
            .query("user:kestra-io language:java is:public")
            .sort(Search.Sort.STARS)
            .build();

        Search.FileOutput run = task.run(runContext);

        assertThat(run.getUri(), is(notNullValue()));
    }

    @Test
    void testParameters() throws Exception {
        RunContext runContext = runContextFactory.of();

        Search task = Search.builder()
            .user("kestra-io")
            .language("java")
            .visibility(Search.Visibility.PUBLIC)
            .sort(Search.Sort.STARS)
            .build();

        Search.FileOutput run = task.run(runContext);

        assertThat(run.getUri(), is(notNullValue()));
    }
}
