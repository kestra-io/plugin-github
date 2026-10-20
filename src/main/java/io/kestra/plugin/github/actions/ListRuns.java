package io.kestra.plugin.github.actions;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.github.AbstractGithubSearchTask;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.function.Function;

import static io.kestra.plugin.github.actions.RunUtils.collect;
import static io.kestra.plugin.github.actions.RunUtils.resolveWorkflow;
import static io.kestra.plugin.github.actions.RunUtils.toMap;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "List workflow runs",
    description = "Lists GitHub Actions workflow runs of a repository, newest first, with optional filters on workflow, branch, actor, event, status, conclusion and creation date. Use `fetchType` to return the runs inline (`FETCH`), only the first one (`FETCH_ONE`), or write them to Kestra internal storage (`STORE`, the default). The token needs the `actions:read` permission."
)
@Plugin(
    examples = {
        @Example(
            title = "List the 10 latest failed runs of a workflow on the main branch.",
            full = true,
            code = """
                id: github_actions_list_runs
                namespace: company.team

                tasks:
                  - id: list_runs
                    type: io.kestra.plugin.github.actions.ListRuns
                    oauthToken: "{{ secret('GITHUB_ACCESS_TOKEN') }}"
                    repository: my-org/my-repo
                    workflowId: ci.yml
                    branch: main
                    conclusion: FAILURE
                    limit: 10
                    fetchType: FETCH

                  - id: log_count
                    type: io.kestra.plugin.core.log.Log
                    message: "Found {{ outputs.list_runs.size }} failed runs"
                """
        )
    }
)
public class ListRuns extends AbstractGithubSearchTask implements RunnableTask<AbstractGithubSearchTask.Output> {
    @Schema(
        title = "Target repository",
        description = "Repository in `owner/repo` format."
    )
    @PluginProperty(group = "source")
    @NotNull
    private Property<String> repository;

    @Schema(
        title = "Workflow",
        description = "Only return runs of this workflow, given as its numeric id or its file name such as `ci.yml`. The filter is applied after fetching, so at most the 500 most recent runs of the repository are examined."
    )
    @PluginProperty(group = "processing")
    private Property<String> workflowId;

    @Schema(title = "Branch", description = "Only return runs for this branch.")
    @PluginProperty(group = "processing")
    private Property<String> branch;

    @Schema(title = "Actor", description = "Only return runs triggered by this GitHub user.")
    @PluginProperty(group = "processing")
    private Property<String> actor;

    @Schema(title = "Event", description = "Only return runs triggered by this event, for example `PUSH` or `WORKFLOW_DISPATCH`.")
    @PluginProperty(group = "processing")
    private Property<RunEvent> event;

    @Schema(title = "Status", description = "Only return runs with this status, for example `QUEUED`, `IN_PROGRESS` or `COMPLETED`.")
    @PluginProperty(group = "processing")
    private Property<RunStatus> status;

    @Schema(title = "Conclusion", description = "Only return runs with this conclusion, for example `SUCCESS` or `FAILURE`.")
    @PluginProperty(group = "processing")
    private Property<RunConclusion> conclusion;

    @Schema(
        title = "Created",
        description = "Only return runs created within this date range, using GitHub's date syntax, for example `>=2026-10-01` or `2026-10-01..2026-10-15`."
    )
    @PluginProperty(group = "processing")
    private Property<String> created;

    @Schema(title = "Limit", description = "Maximum number of runs to return, between 1 and 100 (default 30).")
    @Builder.Default
    @PluginProperty(group = "processing")
    private Property<Integer> limit = Property.ofValue(30);

    @Override
    public Output run(RunContext runContext) throws Exception {
        var max = runContext.render(limit).as(Integer.class).orElse(30);
        if (max < 1 || max > 100) {
            throw new IllegalArgumentException("`limit` must be between 1 and 100, got " + max);
        }

        var repo = connect(runContext).getRepository(
            runContext.render(repository).as(String.class)
                .orElseThrow(() -> new IllegalArgumentException("`repository` is required, in `owner/repo` format"))
        );
        var query = repo.queryWorkflowRuns();

        runContext.render(branch).as(String.class).ifPresent(query::branch);
        runContext.render(actor).as(String.class).ifPresent(query::actor);
        runContext.render(event).as(RunEvent.class).ifPresent(v -> query.event(v.value()));
        runContext.render(status).as(RunStatus.class).ifPresent(v -> query.status(v.toSdk()));
        runContext.render(conclusion).as(RunConclusion.class).ifPresent(v -> query.conclusion(v.toSdk()));
        runContext.render(created).as(String.class).ifPresent(query::created);

        var rWorkflowId = runContext.render(workflowId).as(String.class);
        var workflow = rWorkflowId.isPresent() ? resolveWorkflow(repo, rWorkflowId.get()) : null;

        var runs = new ArrayList<java.util.Map<String, Object>>();
        for (var run : collect(query, workflow, max)) {
            runs.add(toMap(run));
        }

        var rFetchType = runContext.render(fetchType).as(FetchType.class).orElse(FetchType.STORE);
        return handleFetch(runContext, runs, Function.identity(), rFetchType);
    }
}