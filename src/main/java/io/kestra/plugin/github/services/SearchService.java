package io.kestra.plugin.github.services;

import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.FileSerde;
import io.kestra.plugin.github.model.*;
import org.kohsuke.github.*;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import static io.kestra.core.utils.Rethrow.throwConsumer;
import static io.kestra.core.utils.Rethrow.throwFunction;

abstract public class SearchService {
    static public FileOutput run(
        RunContext runContext,
        PagedSearchIterable<? extends GHObject> items,
        GitHub gitHub
    ) throws IOException {
        File tempFile = runContext.workingDir().createTempFile(".ion").toFile();
        try (BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(tempFile))) {

            items
                .toList()
                .stream()
                .map(throwFunction(ghObject -> getDetails(ghObject, gitHub.isAnonymous())))
                .forEachOrdered(throwConsumer(user -> FileSerde.write(output, user)));

            output.flush();

            return FileOutput
                .builder()
                .uri(runContext.storage().putFile(tempFile))
                .build();
        }
    }

    private static Object getDetails(GHObject ghObject, boolean isAnonymous) throws IOException {
        return switch (ghObject) {
            case GHUser user -> new UserDetails(user, isAnonymous);
            case GHRepository repository -> new RepositoryDetails(repository, isAnonymous);
            case GHPullRequest pullRequest -> new PullRequestDetails(pullRequest, isAnonymous);
            case GHIssue issue -> new IssueDetails(issue, isAnonymous);
            default -> null;
        };
    }
}
