package io.kestra.plugin.github.repositories;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.configurations.HttpConfiguration;
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Download a repository archive",
    description = """
        Downloads a GitHub repository archive for a commit SHA, branch or tag and streams it to Kestra internal storage. \
        When `ref` is omitted, the repository default branch is used. The token needs the `contents:read` permission. \
        It is optional for public repositories, which are then subject to GitHub's anonymous rate limit."""
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
                    message: "Stored {{ outputs.download_archive.ref }} archive at {{ outputs.download_archive.uri }} ({{ outputs.download_archive.size }} bytes)"
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
    private static final int MAX_REDIRECTS = 5;

    @Schema(
        title = "Target repository",
        description = """
            Repository in `owner/repo` format to download."""
    )
    @PluginProperty(group = "main")
    @NotNull
    private Property<String> repository;

    @Schema(
        title = "Git ref",
        description = """
            Branch, tag or commit SHA to archive. Defaults to the repository default branch when unset."""
    )
    @PluginProperty(group = "main")
    private Property<String> ref;

    @Schema(
        title = "Archive format",
        description = """
            `ZIP` stores a zipball as `.zip`. `TAR_GZ` stores GitHub's gzip-compressed tarball as `.tar.gz`. Defaults to `ZIP`."""
    )
    @Builder.Default
    @PluginProperty(group = "main")
    private Property<Format> format = Property.ofValue(Format.ZIP);

    @Override
    public Output run(RunContext runContext) throws Exception {
        var rRepository = runContext.render(repository).as(String.class).orElseThrow(() -> new IllegalArgumentException("`repository` is required, in `owner/repo` format"));
        var rFormat = runContext.render(this.format).as(Format.class).orElse(Format.ZIP);
        var gitHub = connect(runContext);
        var rRef = runContext.render(this.ref).as(String.class).filter(value -> !value.isBlank()).orElse(null);
        if (rRef == null) {
            rRef = gitHub.getRepository(rRepository).getDefaultBranch();
        }

        var encodedRef = Arrays.stream(rRef.split("/"))
            .map(segment -> URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"))
            .collect(Collectors.joining("/"));
        var archiveUri = URI.create("%s/repos/%s/%s/%s".formatted(gitHub.getApiUrl(), rRepository, rFormat.endpoint, encodedRef));
        var tempFile = runContext.workingDir().createTempFile(rFormat.extension);
        var httpConfig = HttpConfiguration.builder()
            .followRedirects(Property.ofValue(false))
            .allowFailed(Property.ofValue(true))
            .build();
        var token = findToken(runContext);
        try (var client = new HttpClient(runContext, httpConfig)) {
            var target = archiveUri;
            HttpResponse<Void> response = null;
            for (var hop = 0; hop <= MAX_REDIRECTS; hop++) {
                var request = HttpRequest.builder()
                    .uri(target)
                    .addHeader("Accept", "application/vnd.github+json");
                // Renamed repositories redirect on the API host, then GitHub redirects to a signed URL on another host that must not get the token.
                if (token.isPresent() && isSameOrigin(target, archiveUri)) {
                    request.addHeader("Authorization", "Bearer %s".formatted(token.get()));
                }
                response = download(client, request.build(), tempFile);

                var location = response.getHeaders().firstValue("Location");
                if (response.getStatus().getCode() / 100 != 3 || location.isEmpty()) {
                    break;
                }
                target = target.resolve(location.get());
            }

            var status = response.getStatus().getCode();
            var isRateLimited = status == 429 || (status == 403 && "0".equals(response.getHeaders().firstValue("x-ratelimit-remaining").orElse(null)));
            if (isRateLimited) {
                throw new IllegalStateException(
                    "GitHub rate limit reached (HTTP %d) while downloading %s. Retry later, or set a token for a higher limit.".formatted(status, rRepository)
                );
            }
            if (status == 404) {
                throw new IllegalArgumentException(token.isPresent()
                    ? "No archive found for %s at ref `%s`. Check that the ref exists and the token has the `contents:read` permission.".formatted(rRepository, rRef)
                    : "No archive found for %s at ref `%s`. Check that the ref exists, and set `oauthToken` or `appInstallationToken` for a private repository.".formatted(rRepository, rRef)
                );
            }
            if (token.isPresent() && (status == 401 || status == 403)) {
                throw new IllegalArgumentException(
                    "GitHub rejected the token (HTTP %d). Check that it is valid and has the `contents:read` permission on %s.".formatted(status, rRepository)
                );
            }
            if (status / 100 != 2) {
                throw new IllegalStateException("Failed to download the archive of %s at ref `%s` (HTTP %d).".formatted(rRepository, rRef, status));
            }
        }

        var size = Files.size(tempFile);
        var uri = runContext.storage().putFile(tempFile.toFile());
        runContext.logger().info("Downloaded {} archive of {} at {} ({} bytes)", rFormat, rRepository, rRef, size);

        return Output.builder()
            .uri(uri)
            .size(size)
            .ref(rRef)
            .build();
    }

    private static boolean isSameOrigin(URI uri, URI origin) {
        return Objects.equals(uri.getScheme(), origin.getScheme())
            && Objects.equals(uri.getHost(), origin.getHost())
            && uri.getPort() == origin.getPort();
    }

    private static HttpResponse<Void> download(HttpClient client, HttpRequest request, Path target) throws Exception {
        return client.request(request, response -> {
            if (response.getStatus().getCode() / 100 != 2 || response.getBody() == null) {
                return;
            }
            try (var output = Files.newOutputStream(target)) {
                response.getBody().transferTo(output);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    public enum Format {
        TAR_GZ("tarball", ".tar.gz"),
        ZIP("zipball", ".zip");

        private final String endpoint;
        private final String extension;

        Format(String endpoint, String extension) {
            this.endpoint = endpoint;
            this.extension = extension;
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Archive URI",
            description = """
                Kestra internal storage URI of the downloaded archive."""
        )
        private final URI uri;

        @Schema(
            title = "Archive size",
            description = """
                Size of the stored archive in bytes."""
        )
        private final Long size;

        @Schema(
            title = "Resolved ref",
            description = """
                Branch, tag or commit SHA that was archived. The repository default branch when `ref` is omitted."""
        )
        private final String ref;
    }
}
