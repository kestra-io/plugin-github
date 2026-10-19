package io.kestra.plugin.github.actions;

import static io.kestra.plugin.github.actions.RunUtils.parseEnum;
import static io.kestra.plugin.github.actions.RunUtils.toMap;

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
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHWorkflowRun;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "List workflow runs",
    description = "Lists GitHub Actions workflow runs of a repository, newest first, with optional filters on branch, actor, event, status and conclusion. The token needs the `actions:read` permission."
)
@Plugin(
    examples = {
        @Example(
            title = "List the 10 latest failed runs on the main branch.",
            full = true,
            code = """
                id: github_actions_list_runs
                namespace: company.team

                tasks:
                  - id: list_runs
                    type: io.kestra.plugin.github.actions.ListRuns
                    oauthToken: "{{ secret('GITHUB_ACCESS_TOKEN') }}"
                    repository: my-org/my-repo
                    branch: main
                    conclusion: failure
                    limit: 10

                  - id: log_count
                    type: io.kestra.plugin.core.log.Log
                    message: "Found {{ outputs.list_runs.size }} failed runs"
                """
        )
    }
)
public class ListRuns extends AbstractGithubTask implements RunnableTask<ListRuns.Output> {
    @Schema(
        title = "Target repository",
        description = "Repository in `owner/repo` format."
    )
    @PluginProperty(group = "source")
    @NotNull
    private Property<String> repository;

    @Schema(title = "Branch", description = "Only return runs for this branch.")
    @PluginProperty(group = "processing")
    private Property<String> branch;

    @Schema(title = "Actor", description = "Only return runs triggered by this GitHub user.")
    @PluginProperty(group = "processing")
    private Property<String> actor;

    @Schema(title = "Event", description = "Only return runs triggered by this event, for example `push` or `workflow_dispatch`.")
    @PluginProperty(group = "processing")
    private Property<String> event;

    @Schema(title = "Status", description = "Only return runs with this status, for example `queued`, `in_progress` or `completed`.")
    @PluginProperty(group = "processing")
    private Property<String> status;

    @Schema(title = "Conclusion", description = "Only return runs with this conclusion, for example `success` or `failure`.")
    @PluginProperty(group = "processing")
    private Property<String> conclusion;

    @Schema(title = "Limit", description = "Maximum number of runs to return, between 1 and 100 (default 30).")
    @Builder.Default
    @PluginProperty(group = "processing")
    private Property<Integer> limit = Property.ofValue(30);

    @Override
    public Output run(RunContext runContext) throws Exception {
        var gitHub = connect(runContext);
        var repo = gitHub.getRepository(runContext.render(repository).as(String.class).orElseThrow(() -> new IllegalArgumentException("`repository` is required, in `owner/repo` format")));
        var query = repo.queryWorkflowRuns();

        runContext.render(branch).as(String.class).ifPresent(query::branch);
        runContext.render(actor).as(String.class).ifPresent(query::actor);
        runContext.render(event).as(String.class).ifPresent(v -> query.event(parseEnum(GHEvent.class, "event", v)));
        runContext.render(status).as(String.class).ifPresent(v -> query.status(parseEnum(GHWorkflowRun.Status.class, "status", v)));
        runContext.render(conclusion).as(String.class).ifPresent(v -> query.conclusion(parseEnum(GHWorkflowRun.Conclusion.class, "conclusion", v)));

        int max = runContext.render(limit).as(Integer.class).orElse(30);
        if (max < 1 || max > 100) {
            throw new IllegalArgumentException("`limit` must be between 1 and 100, got " + max);
        }
        List<Map<String, Object>> runs = new ArrayList<>();

        for (GHWorkflowRun run : query.list().withPageSize(Math.min(max, 100))) {
            if (runs.size() >= max) {
                break;
            }
            runs.add(toMap(run));
        }

        return Output.builder()
            .runs(runs)
            .size(runs.size())
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Runs", description = "Matching runs with id, number, status, conclusion, event, branch, sha, URL and timestamps.")
        private final List<Map<String, Object>> runs;

        @Schema(title = "Size", description = "Number of runs returned.")
        private final Integer size;
    }
}