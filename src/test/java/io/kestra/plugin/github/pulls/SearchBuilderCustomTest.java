package io.kestra.plugin.github.pulls;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SearchBuilderCustomTest {
    @Test
    void mentionsAddsQualifier() throws Exception {
        var builder = new Search.GHPullRequestSearchBuilderCustom(mock(org.kohsuke.github.GitHub.class));

        builder.mentions("octocat");

        var field = Search.GHPullRequestSearchBuilderCustom.class.getDeclaredField("terms");
        field.setAccessible(true);

        @SuppressWarnings("unchecked")
        var terms = (List<String>) field.get(builder);

        assertThat(terms).contains("mentions:octocat");
    }
}
