package io.kestra.plugin.github.actions;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import org.kohsuke.github.GHWorkflowRun;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static io.kestra.plugin.github.actions.RunUtils.lower;
import static io.kestra.plugin.github.actions.RunUtils.toInstant;

@Builder
@Getter
public class RunSummary {
    @Schema(title = "Run ID", description = "Id of the workflow run.")
    private final Long runId;

    @Schema(title = "Run number", description = "Sequential number of the run within the workflow.")
    private final Long runNumber;

    @Schema(title = "Status", description = "Run status such as `queued`, `in_progress` or `completed`.")
    private final String status;

    @Schema(title = "Conclusion", description = "Run conclusion such as `success` or `failure`. Only set when the run is completed.")
    private final String conclusion;

    @Schema(title = "Event", description = "Event that triggered the run, for example `push` or `workflow_dispatch`.")
    private final String event;

    @Schema(title = "Head branch", description = "Branch the run was executed on.")
    private final String headBranch;

    @Schema(title = "Head SHA", description = "Commit SHA the run was executed on.")
    private final String headSha;

    @Schema(title = "Run URL", description = "URL of the run on GitHub.")
    private final String htmlUrl;

    @Schema(title = "Created at", description = "When the run was created.")
    private final Instant createdAt;

    @Schema(title = "Updated at", description = "When the run was last updated.")
    private final Instant updatedAt;

    static RunSummary of(GHWorkflowRun run) throws IOException {
        var url = run.getHtmlUrl();
        return RunSummary.builder()
            .runId(run.getId())
            .runNumber(run.getRunNumber())
            .status(lower(run.getStatus()))
            .conclusion(lower(run.getConclusion()))
            .event(lower(run.getEvent()))
            .headBranch(run.getHeadBranch())
            .headSha(run.getHeadSha())
            .htmlUrl(url == null ? null : url.toString())
            .createdAt(toInstant(run.getCreatedAt()))
            .updatedAt(toInstant(run.getUpdatedAt()))
            .build();
    }

    Map<String, Object> toMap() {
        var map = new LinkedHashMap<String, Object>();
        map.put("runId", runId);
        map.put("runNumber", runNumber);
        map.put("status", status);
        map.put("conclusion", conclusion);
        map.put("event", event);
        map.put("headBranch", headBranch);
        map.put("headSha", headSha);
        map.put("htmlUrl", htmlUrl);
        map.put("createdAt", createdAt);
        map.put("updatedAt", updatedAt);
        return map;
    }
}
