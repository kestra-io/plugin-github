package io.kestra.plugin.github;

import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.FileSerde;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.util.List;

import org.kohsuke.github.PagedSearchIterable;
import java.util.Map;
import java.util.function.Function;
import io.kestra.core.models.annotations.PluginProperty;
import org.kohsuke.github.PagedSearchIterable;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractGithubSearchTask extends AbstractGithubTask {
    @Schema(
        title = "Result handling mode",
        description = "Controls how hits are exposed in outputs; default `STORE` writes all hits to Kestra storage and returns a URI. `FETCH` returns all hits in the response, `FETCH_ONE` returns only the first hit, and `NONE` leaves outputs empty."
    )
    @Builder.Default
    @PluginProperty(group = "execution")
    protected Property<FetchType> fetchType = Property.ofValue(FetchType.STORE);


    protected <T> Output handleFetch(RunContext runContext, PagedSearchIterable<T> results, Function<T, Map<String, Object>> mapper, FetchType fetchType) throws Exception {
        List<T> items = results.toList();
        return handleFetch(runContext, items, mapper, fetchType, (long) results.getTotalCount());
    }

    protected <T> Output handleFetch(RunContext runContext, List<T> results, Function<T, Map<String, Object>> mapper, FetchType fetchType) throws Exception {
        return handleFetch(runContext, results, mapper, fetchType, null);
    }

    protected <T> Output handleFetch(RunContext runContext, List<T> results, Function<T, Map<String, Object>> mapper, FetchType fetchType, Long total) throws Exception {

        List<Map<String, Object>> mapped = results.stream()
            .map(mapper)
            .toList();

        switch (fetchType) {
            case FETCH:
                return Output.builder()
                    .total(total)
                    .rows(mapped)
                    .size(mapped.size())
                    .build();

            case FETCH_ONE:
                return Output.builder()
                    .total(total)
                    .row(mapped.isEmpty() ? null : mapped.getFirst())
                    .size(mapped.isEmpty() ? 0 : 1)
                    .build();

            case STORE:
                File tempFile = runContext.workingDir().createTempFile(".ion").toFile();

                try (var output = new BufferedOutputStream(new FileOutputStream(tempFile), FileSerde.BUFFER_SIZE)) {

                    for (Map<String, Object> item : mapped) {
                        FileSerde.write(output, item);
                    }
                }

                return Output.builder()
                    .total(total)
                    .uri(runContext.storage().putFile(tempFile))
                    .size(mapped.size())
                    .build();

            default:
                return Output.builder()
                    .total(total).size(0).build();
        }
    }

        @Builder
        @Getter
        public static class Output implements io.kestra.core.models.tasks.Output {
            @Schema(
                title = "Returned hit count",
                description = "Number of hits included in outputs for the selected fetch type."
            )
            private Integer size;

            @Schema(
                title = "Total hits reported",
                description = "Total hits reported by GitHub search, regardless of pagination."
            )
            private Long total;

            @Schema(
                title = "Fetched hits",
                description = "Available only when `fetchType=FETCH`; contains hit sources for the current response page."
            )
            private List<Map<String, Object>> rows;

            @Schema(
                title = "First hit",
                description = "Available only when `fetchType=FETCH_ONE`; contains the first hit source."
            )
            private Map<String, Object> row;

            @Schema(
                title = "Stored hits URI",
                description = "Available only when `fetchType=STORE`; Kestra internal storage path to the Ion file."
            )
            private URI uri;
        }
}