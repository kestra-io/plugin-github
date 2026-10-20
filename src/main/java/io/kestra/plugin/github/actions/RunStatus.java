package io.kestra.plugin.github.actions;

import org.kohsuke.github.GHWorkflowRun;

public enum RunStatus {
    QUEUED,
    IN_PROGRESS,
    COMPLETED,
    WAITING,
    REQUESTED,
    PENDING;

    GHWorkflowRun.Status toSdk() {
        return GHWorkflowRun.Status.valueOf(name());
    }
}
