package io.kestra.plugin.github.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.net.URI;

@Builder
@Getter
public class FileOutput implements io.kestra.core.models.tasks.Output {
    @Schema(
        title = "Output file URI",
        description = "URI of the file written to Kestra internal storage, typically using the `kestra://` scheme"
    )
    private URI uri;
}
