package io.kestra.plugin.github.actions;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.github.AbstractGithubTask;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHWorkflow;
import org.kohsuke.github.GHWorkflowRun;
import org.slf4j.Logger;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static io.kestra.plugin.github.actions.RunUtils.lower;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Dispatch a workflow run",
    description = "Triggers a `workflow_dispatch` event for a workflow in the target repository. The authenticated token must be allowed to read the repository and run workflows on the selected ref. Outputs the id and URL of the created run. With `wait: true`, polls until the run completes and fails the task when the conclusion is not `success`. The created run is detected as the newest `workflow_dispatch` run of this workflow on the ref, so two executions dispatching the same workflow and ref at the same moment can report each other's run."
)
@Plugin(
    examples = {
        @Example(
            title = "Trigger a GitHub Action workflow manually.",
            full = true,
            code = """
                id: run_github_action_workflow_flow
                namespace: company.team

                tasks:
                  - id: run_workflow
                    type: io.kestra.plugin.github.actions.RunWorkflow
                    oauthToken: "{{ secret('GITHUB_ACCESS_TOKEN') }}"
                    repository: your_owner/your_repository
                    workflowId: your_workflow_id
                    ref: your_branch_or_tag_name
                    inputs:
                      foo:bar
                """
        ),
        @Example(
            title = "Run a workflow and wait for the result.",
            full = true,
            code = """
                id: github_actions_run_and_wait
                namespace: company.team

                tasks:
                  - id: run_workflow
                    type: io.kestra.plugin.github.actions.RunWorkflow
                    oauthToken: "{{ secret('GITHUB_ACCESS_TOKEN') }}"
                    repository: my-org/my-repo
                    workflowId: ci.yml
                    ref: main
                    wait: true
                    waitTimeout: PT30M

                  - id: log_result
                    type: io.kestra.plugin.core.log.Log
                    message: "Run {{ outputs.run_workflow.runId }} ended with {{ outputs.run_workflow.conclusion }}: {{ outputs.run_workflow.htmlUrl }}"
                """
        )
    }
)
public class RunWorkflow extends AbstractGithubTask implements RunnableTask<RunWorkflow.Output> {
    private static final Duration LOOKUP_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration LOOKUP_INTERVAL = Duration.ofSeconds(2);

    @JsonIgnore
    @Getter(AccessLevel.NONE)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private final AtomicReference<Runnable> killable = new AtomicReference<>();

    @JsonIgnore
    @Getter(AccessLevel.NONE)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private final AtomicBoolean isKilled = new AtomicBoolean(false);

    @Schema(
        title = "Target repository",
        description = "Repository in `owner/repo` format containing the workflow. The authenticated token must have access to this repository."
    )
    @PluginProperty(group = "destination")
    private Property<String> repository;

    @Schema(
        title = "Workflow ID or filename",
        description = "Workflow identifier accepted by the GitHub API such as a numeric ID or a workflow filename like `build.yml`"
    )
    @PluginProperty(group = "advanced")
    private Property<String> workflowId;

    @Schema(
        title = "Workflow ref",
        description = "Branch or tag name used to resolve the workflow file. This ref must exist in the target repository."
    )
    @PluginProperty(group = "advanced")
    private Property<String> ref;

    @Schema(
        title = "Workflow inputs map",
        description = "Key/value payload passed to workflow `inputs`. Property values are rendered before the dispatch request is sent",
        requiredMode = Schema.RequiredMode.NOT_REQUIRED
    )
    @PluginProperty(group = "source")
    private Property<Map<String, Object>> inputs;

    @Schema(
        title = "Wait for completion",
        description = "When `true`, looks for the created run for up to 30 seconds, then polls it until it is completed. When `false` (default), the task looks for the run once right after the dispatch and returns; the run outputs are empty if GitHub has not registered the run yet."
    )
    @Builder.Default
    @PluginProperty(group = "execution")
    private Property<Boolean> wait = Property.ofValue(false);

    @Schema(
        title = "Poll interval",
        description = "Delay between two status checks when `wait` is `true`."
    )
    @Builder.Default
    @PluginProperty(group = "execution")
    private Property<Duration> pollInterval = Property.ofValue(Duration.ofSeconds(5));

    @Schema(
        title = "Wait timeout",
        description = "Maximum time to wait for the run to complete when `wait` is `true`. Separate from the task-level `timeout`, which stops the task on the worker."
    )
    @Builder.Default
    @PluginProperty(group = "execution")
    private Property<Duration> waitTimeout = Property.ofValue(Duration.ofHours(1));

    @Schema(
        title = "Cancel the run on timeout",
        description = "When `true`, cancels the GitHub run if the wait timeout is reached."
    )
    @Builder.Default
    @PluginProperty(group = "reliability")
    private Property<Boolean> cancelOnTimeout = Property.ofValue(false);

    @Schema(
        title = "Fail on unsuccessful conclusion",
        description = "When `true` (default), the task fails if the finished run's conclusion is not `success`, which includes `skipped` and `neutral`. Only used when `wait` is `true`."
    )
    @Builder.Default
    @PluginProperty(group = "reliability")
    private Property<Boolean> failOnConclusion = Property.ofValue(true);

    @Override
    public Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var gitHub = connect(runContext);

        var repo = gitHub.getRepository(runContext.render(repository).as(String.class).orElse(null));
        var workflow = repo.getWorkflow(runContext.render(workflowId).as(String.class).orElse(null));
        var rRef = runContext.render(ref).as(String.class).orElse(null);
        var rWait = runContext.render(wait).as(Boolean.class).orElse(false);

        var lastKnownRunId = 0L;
        var canTrackRun = true;
        try {
            lastKnownRunId = latestRunId(repo, workflow, rRef);
        } catch (IOException | RuntimeException e) {
            canTrackRun = false;
            logger.warn("Unable to read existing workflow runs, the run id will not be available: {}", e.getMessage());
        }

        workflow.dispatch(rRef, runContext.render(inputs).asMap(String.class, Object.class));

        if (!canTrackRun) {
            if (rWait) {
                throw new IllegalStateException("Cannot wait for the workflow run because its runs could not be listed");
            }
            return Output.builder().build();
        }

        var lookupTimeout = rWait ? LOOKUP_TIMEOUT : Duration.ZERO;
        var run = findNewRun(repo, workflow, rRef, lastKnownRunId, lookupTimeout);
        if (run == null) {
            if (rWait) {
                throw new IllegalStateException("The dispatched workflow run was not found after " + LOOKUP_TIMEOUT);
            }
            logger.warn("The dispatched workflow run is not visible on GitHub yet, returning without run details");
            return Output.builder().build();
        }

        logger.info("Workflow run {} started: {}", run.getId(), run.getHtmlUrl());

        if (!rWait) {
            return toOutput(run);
        }

        var startedRunId = run.getId();
        killable.set(() -> cancelRun(repo, startedRunId, logger));
        if (isKilled.get()) {
            cancelRun(repo, startedRunId, logger);
            throw new IllegalStateException("The execution was killed, workflow run " + startedRunId + " was cancelled");
        }

        return toOutput(waitForCompletion(runContext, repo.getWorkflowRun(startedRunId), repo));
    }

    @Override
    public void kill() {
        if (isKilled.compareAndSet(false, true)) {
            Optional.ofNullable(killable.get()).ifPresent(Runnable::run);
        }
    }

    private static void cancelRun(GHRepository repo, long runId, Logger logger) {
        try {
            repo.getWorkflowRun(runId).cancel();
            logger.warn("Execution killed, cancelled workflow run {}", runId);
        } catch (Exception e) {
            logger.warn("Unable to cancel workflow run {}: {}", runId, e.getMessage());
        }
    }

    private GHWorkflowRun latestRun(GHRepository repo, GHWorkflow workflow, String rRef) throws IOException {
        var seen = 0;
        for (var candidate : repo.queryWorkflowRuns().event(GHEvent.WORKFLOW_DISPATCH).branch(rRef).list().withPageSize(30)) {
            if (candidate.getWorkflowId() == workflow.getId()) {
                return candidate;
            }
            if (++seen >= 100) {
                break;
            }
        }
        return null;
    }

    private long latestRunId(GHRepository repo, GHWorkflow workflow, String rRef) throws IOException {
        var latest = latestRun(repo, workflow, rRef);
        return latest == null ? 0 : latest.getId();
    }

    private GHWorkflowRun findNewRun(GHRepository repo, GHWorkflow workflow, String rRef, long lastKnownRunId, Duration lookupTimeout) throws Exception {
        var deadline = Instant.now().plus(lookupTimeout);
        while (true) {
            var candidate = latestRun(repo, workflow, rRef);
            if (candidate != null && candidate.getId() > lastKnownRunId) {
                return candidate;
            }
            if (!Instant.now().plus(LOOKUP_INTERVAL).isBefore(deadline)) {
                return null;
            }
            Thread.sleep(LOOKUP_INTERVAL.toMillis());
        }
    }

    private GHWorkflowRun waitForCompletion(RunContext runContext, GHWorkflowRun run, GHRepository repo) throws Exception {
        var logger = runContext.logger();
        var rPollInterval = runContext.render(pollInterval).as(Duration.class).orElse(Duration.ofSeconds(5));
        var rWaitTimeout = runContext.render(waitTimeout).as(Duration.class).orElse(Duration.ofHours(1));
        if (rPollInterval.isZero() || rPollInterval.isNegative() || rWaitTimeout.isZero() || rWaitTimeout.isNegative()) {
            throw new IllegalArgumentException("`pollInterval` and `waitTimeout` must be positive durations, got " + rPollInterval + " and " + rWaitTimeout);
        }
        var deadline = Instant.now().plus(rWaitTimeout);

        while (run.getStatus() != GHWorkflowRun.Status.COMPLETED) {
            if (Instant.now().isAfter(deadline)) {
                if (runContext.render(cancelOnTimeout).as(Boolean.class).orElse(false)) {
                    logger.warn("Wait timeout reached, cancelling workflow run {}", run.getId());
                    try {
                        run.cancel();
                    } catch (Exception e) {
                        logger.warn("Unable to cancel workflow run {}: {}", run.getId(), e.getMessage());
                    }
                }
                throw new IllegalStateException("Workflow run " + run.getId() + " did not complete within " + rWaitTimeout);
            }
            Thread.sleep(rPollInterval.toMillis());
            run = repo.getWorkflowRun(run.getId());
            logger.debug("Workflow run {} status: {}", run.getId(), run.getStatus());
        }

        var failOnError = runContext.render(failOnConclusion).as(Boolean.class).orElse(true);
        if (failOnError && run.getConclusion() != GHWorkflowRun.Conclusion.SUCCESS) {
            throw new IllegalStateException(
                "Workflow run " + run.getId() + " ended with conclusion '" + lower(run.getConclusion()) + "': " + run.getHtmlUrl()
            );
        }

        return run;
    }

    private Output toOutput(GHWorkflowRun run) throws IOException {
        var url = run.getHtmlUrl();
        return Output.builder()
            .runId(run.getId())
            .htmlUrl(url == null ? null : url.toString())
            .status(lower(run.getStatus()))
            .conclusion(lower(run.getConclusion()))
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Run ID", description = "Id of the workflow run created by the dispatch. Empty if the run could not be found.")
        private final Long runId;

        @Schema(title = "Run URL", description = "URL of the workflow run on GitHub.")
        private final String htmlUrl;

        @Schema(title = "Run status", description = "Status of the run when the task ended, for example `queued`, `in_progress` or `completed`.")
        private final String status;

        @Schema(title = "Run conclusion", description = "Conclusion of the run such as `success` or `failure`. Only set when the run is completed.")
        private final String conclusion;
    }
}
