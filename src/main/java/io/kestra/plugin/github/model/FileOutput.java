package io.kestra.plugin.github.model;

import lombok.Builder;
import lombok.Getter;

import java.net.URI;

@Builder
@Getter
public class FileOutput implements io.kestra.core.models.tasks.Output {
    private URI uri;
}
