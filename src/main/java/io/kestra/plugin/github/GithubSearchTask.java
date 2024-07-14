package io.kestra.plugin.github;

import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.FileSerde;
import io.kestra.plugin.github.model.IssueDetails;
import io.kestra.plugin.github.model.PullRequestDetails;
import io.kestra.plugin.github.model.RepositoryDetails;
import io.kestra.plugin.github.model.UserDetails;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.kohsuke.github.*;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;

import static io.kestra.core.utils.Rethrow.throwConsumer;
import static io.kestra.core.utils.Rethrow.throwFunction;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
abstract public class GithubSearchTask extends GithubConnector {

    protected FileOutput run(RunContext runContext, PagedSearchIterable<? extends GHObject> items) throws IOException {
        File tempFile = runContext.workingDir().createTempFile(".ion").toFile();
        try (BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(tempFile))) {

            items
                .toList()
                .stream()
                .map(
                    throwFunction(GithubSearchTask::getDetails)
                )
                .forEachOrdered(
                    throwConsumer(
                        user -> FileSerde.write(output, user)
                    )
                );

            output.flush();

            return FileOutput
                .builder()
                .uri(runContext.storage().putFile(tempFile))
                .build();
        }
    }

    private static Object getDetails(GHObject ghObject) throws IOException {
        return switch (ghObject) {
            case GHUser user -> new UserDetails(user);
            case GHRepository repository -> new RepositoryDetails(repository);
            case GHPullRequest pullRequest -> new PullRequestDetails(pullRequest);
            case GHIssue issue -> new IssueDetails(issue);
            default -> null;
        };
    }

    @Builder
    @Getter
    public static class FileOutput implements io.kestra.core.models.tasks.Output {
        private URI uri;
    }

}
