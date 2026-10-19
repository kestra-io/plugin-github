package io.kestra.plugin.github.actions;

import static io.kestra.plugin.github.actions.RunUtils.lower;
import static io.kestra.plugin.github.actions.RunUtils.toInstant;

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
import org.kohsuke.github.GHWorkflowJob;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Get a workflow run",
    description = "Fetches a GitHub Actions workflow run by id and returns its status, conclusion and metadata. Optionally includes the jobs of the run with their own status and conclusion. The token needs the `actions:read` permission."
)
@Plugin(
    examples = {
        @Example(
            title = "Get the status and jobs of a workflow run.",
            full = true,
            code = """
                id: github_actions_get_run
                namespace: company.team

                tasks:
                  - id: get_run
                    type: io.kestra.plugin.github.actions.GetRun
                    oauthToken: "{{ secret('GITHUB_ACCESS_TOKEN') }}"
                    repository: my-org/my-repo
                    runId: 123456789
                    includeJobs: true

                  - id: log_result
                    type: io.kestra.plugin.core.log.Log
                    message: "Run {{ outputs.get_run.runNumber }} is {{ outputs.get_run.status }} with conclusion {{ outputs.get_run.conclusion }}"
                """
        )
    }
)
public class GetRun extends AbstractGithubTask implements RunnableTask<GetRun.Output> {
    @Schema(
        title = "Target repository",
        description = "Repository in `owner/repo` format containing the workflow run."
    )
    @PluginProperty(group = "source")
    @NotNull
    private Property<String> repository;

    @Schema(
        title = "Run ID",
        description = "Numeric id of the workflow run, for example the `runId` output of `RunWorkflow`."
    )
    @PluginProperty(group = "main")
    @NotNull
    private Property<Long> runId;

    @Schema(
        title = "Include jobs",
        description = "When `true`, also returns the jobs of the run with their status and conclusion."
    )
    @Builder.Default
    @PluginProperty(group = "advanced")
    private Property<Boolean> includeJobs = Property.ofValue(false);

    @Override
    public Output run(RunContext runContext) throws Exception {
        var gitHub = connect(runContext);
        var repo = gitHub.getRepository(runContext.render(repository).as(String.class).orElseThrow(() -> new IllegalArgumentException("`repository` is required, in `owner/repo` format")));
        var run = repo.getWorkflowRun(runContext.render(runId).as(Long.class).orElseThrow(() -> new IllegalArgumentException("`runId` is required - use the `runId` output of RunWorkflow or the id in the run URL")));

        List<Map<String, Object>> jobs = null;
        if (runContext.render(includeJobs).as(Boolean.class).orElse(false)) {
            jobs = new ArrayList<>();
            for (GHWorkflowJob job : run.listJobs()) {
                var map = new LinkedHashMap<String, Object>();
                map.put("id", job.getId());
                map.put("name", job.getName());
                map.put("status", lower(job.getStatus()));
                map.put("conclusion", lower(job.getConclusion()));
                jobs.add(map);
            }
        }

        var url = run.getHtmlUrl();

        return Output.builder()
            .runId(run.getId())
            .runNumber(run.getRunNumber())
            .status(lower(run.getStatus()))
            .conclusion(lower(run.getConclusion()))
            .headSha(run.getHeadSha())
            .htmlUrl(url == null ? null : url.toString())
            .createdAt(toInstant(run.getCreatedAt()))
            .updatedAt(toInstant(run.getUpdatedAt()))
            .jobs(jobs)
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Run ID", description = "Id of the workflow run.")
        private final Long runId;

        @Schema(title = "Run number", description = "Sequential number of the run within the workflow.")
        private final Long runNumber;

        @Schema(title = "Status", description = "Run status such as `queued`, `in_progress` or `completed`.")
        private final String status;

        @Schema(title = "Conclusion", description = "Run conclusion such as `success` or `failure`. Only set when the run is completed.")
        private final String conclusion;

        @Schema(title = "Head SHA", description = "Commit SHA the run was executed on.")
        private final String headSha;

        @Schema(title = "Run URL", description = "URL of the run on GitHub.")
        private final String htmlUrl;

        @Schema(title = "Created at", description = "When the run was created.")
        private final Instant createdAt;

        @Schema(title = "Updated at", description = "When the run was last updated.")
        private final Instant updatedAt;

        @Schema(title = "Jobs", description = "Jobs of the run with id, name, status and conclusion. Only set when `includeJobs` is `true`.")
        private final List<Map<String, Object>> jobs;
    }
}