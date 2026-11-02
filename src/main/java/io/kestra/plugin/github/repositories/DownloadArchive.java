package io.kestra.plugin.github.repositories;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.github.AbstractGithubTask;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.kohsuke.github.GHRepository;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.net.URI;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Download a repository archive",
    description = "Downloads a GitHub repository archive for a commit SHA, branch or tag and stores it in Kestra internal storage. When `ref` is omitted, the repository default branch is used. The token needs the `contents:read` permission. `ZIP` is the default. `TAR_GZ` stores GitHub's gzip-compressed tarball as `.tar.gz`."
)
@Plugin(
    examples = {
        @Example(
            title = "Download a commit archive with a GitHub App installation token.",
            full = true,
            code = """
                id: github_download_archive
                namespace: company.team

                tasks:
                  - id: github_app_token
                    type: io.kestra.plugin.github.auth.AppToken
                    clientId: "{{ secret('GITHUB_APP_CLIENT_ID') }}"
                    installationId: "52068731"
                    privateKey: "{{ secret('GITHUB_APP_PRIVATE_KEY') }}"

                  - id: download_archive
                    type: io.kestra.plugin.github.repositories.DownloadArchive
                    appInstallationToken: "{{ outputs.github_app_token.token }}"
                    repository: kestra-io/kestra
                    ref: a1b2c3d4e5f6789012345678901234567890abcd
                    format: TAR_GZ

                  - id: log_archive
                    type: io.kestra.plugin.core.log.Log
                    message: "Stored {{ outputs.download_archive.format }} of {{ outputs.download_archive.ref }} at {{ outputs.download_archive.uri }} ({{ outputs.download_archive.size }} bytes)"
                """
        ),
        @Example(
            title = "Download a branch or tag as a zip archive.",
            full = true,
            code = """
                id: github_download_zip
                namespace: company.team

                tasks:
                  - id: download_archive
                    type: io.kestra.plugin.github.repositories.DownloadArchive
                    oauthToken: "{{ secret('GITHUB_ACCESS_TOKEN') }}"
                    repository: kestra-io/kestra
                    ref: develop
                    format: ZIP
                """
        )
    }
)
public class DownloadArchive extends AbstractGithubTask implements RunnableTask<DownloadArchive.Output> {
    @Schema(
        title = "Target repository",
        description = "Repository in `owner/repo` format to download."
    )
    @PluginProperty(group = "source")
    @NotNull
    private Property<String> repository;

    @Schema(
        title = "Git ref",
        description = "Branch, tag or commit SHA to archive. Defaults to the repository default branch when unset."
    )
    @PluginProperty(group = "main")
    private Property<String> ref;

    @Schema(
        title = "Archive format",
        description = "`ZIP` downloads a zipball and stores it as `.zip`. `TAR_GZ` downloads GitHub's gzip-compressed tarball and stores it as `.tar.gz`. Defaults to `ZIP`."
    )
    @Builder.Default
    @PluginProperty(group = "main")
    private Property<Format> format = Property.ofValue(Format.ZIP);

    @Override
    public Output run(RunContext runContext) throws Exception {
        var gitHub = connect(runContext);
        var repositoryName = runContext.render(repository).as(String.class).orElseThrow(() -> new IllegalArgumentException("`repository` is required, in `owner/repo` format"));
        var repo = gitHub.getRepository(repositoryName);
        var format = runContext.render(this.format).as(Format.class).orElse(Format.ZIP);
        var resolvedRef = resolveRef(runContext, repo);

        var extension = format == Format.TAR_GZ ? ".tar.gz" : ".zip";
        var tempFile = runContext.workingDir().createTempFile(extension).toFile();
        try (var output = new BufferedOutputStream(new FileOutputStream(tempFile))) {
            if (format == Format.TAR_GZ) {
                repo.readTar(in -> {
                    in.transferTo(output);
                    return null;
                }, resolvedRef);
            } else {
                repo.readZip(in -> {
                    in.transferTo(output);
                    return null;
                }, resolvedRef);
            }
        }

        var size = tempFile.length();
        var uri = runContext.storage().putFile(tempFile);
        runContext.logger().info(
            "Downloaded {} archive of {} at {} ({} bytes)",
            format,
            repositoryName,
            resolvedRef,
            size
        );

        return Output.builder()
            .uri(uri)
            .size(size)
            .ref(resolvedRef)
            .format(format.name())
            .build();
    }

    private String resolveRef(RunContext runContext, GHRepository repo) throws Exception {
        var rendered = runContext.render(this.ref).as(String.class).filter(value -> !value.isBlank()).orElse(null);
        if (rendered != null) {
            return rendered;
        }
        var defaultBranch = repo.getDefaultBranch();
        if (defaultBranch == null || defaultBranch.isBlank()) {
            throw new IllegalStateException("Repository has no default branch and `ref` was not set");
        }
        return defaultBranch;
    }

    public enum Format {
        TAR_GZ,
        ZIP
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Archive URI",
            description = "Kestra internal storage URI of the downloaded archive."
        )
        private final URI uri;

        @Schema(
            title = "Archive size",
            description = "Size of the stored archive in bytes."
        )
        private final Long size;

        @Schema(
            title = "Resolved ref",
            description = "Branch, tag or commit SHA that was archived. The repository default branch when `ref` is omitted."
        )
        private final String ref;

        @Schema(
            title = "Archive format",
            description = "`TAR_GZ` or `ZIP`."
        )
        private final String format;
    }
}
