package io.kestra.plugin.github.actions;

import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHWorkflow;
import org.kohsuke.github.GHWorkflowRun;
import org.kohsuke.github.GHWorkflowRunQueryBuilder;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class RunUtils {
    static final int MAX_SCAN = 500;

    private RunUtils() {
    }

    static Instant toInstant(Date date) {
        return date == null ? null : date.toInstant();
    }

    static String lower(Enum<?> value) {
        return value == null ? null : value.name().toLowerCase();
    }

    static GHWorkflow resolveWorkflow(GHRepository repo, String workflowId) {
        try {
            return repo.getWorkflow(workflowId);
        } catch (IOException e) {
            throw new IllegalArgumentException(
                "Workflow '" + workflowId + "' could not be loaded from " + repo.getFullName()
                    + " - use its numeric id or its file name such as ci.yml (" + e.getMessage() + ")"
            );
        }
    }

    static List<GHWorkflowRun> collect(GHWorkflowRunQueryBuilder query, GHWorkflow workflow, int max) throws IOException {
        var runs = new ArrayList<GHWorkflowRun>();
        var scanned = 0;
        var pageSize = workflow == null ? Math.min(max, 100) : 100;
        for (var run : query.list().withPageSize(pageSize)) {
            if (runs.size() >= max || scanned++ >= MAX_SCAN) {
                break;
            }
            if (workflow == null || run.getWorkflowId() == workflow.getId()) {
                runs.add(run);
            }
        }
        return runs;
    }

    static Map<String, Object> toMap(GHWorkflowRun run) throws IOException {
        var url = run.getHtmlUrl();
        var map = new LinkedHashMap<String, Object>();
        map.put("runId", run.getId());
        map.put("runNumber", run.getRunNumber());
        map.put("status", lower(run.getStatus()));
        map.put("conclusion", lower(run.getConclusion()));
        map.put("event", lower(run.getEvent()));
        map.put("headBranch", run.getHeadBranch());
        map.put("headSha", run.getHeadSha());
        map.put("htmlUrl", url == null ? null : url.toString());
        map.put("createdAt", toInstant(run.getCreatedAt()));
        map.put("updatedAt", toInstant(run.getUpdatedAt()));
        return map;
    }
}