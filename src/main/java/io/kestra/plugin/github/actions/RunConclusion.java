package io.kestra.plugin.github.actions;

import org.kohsuke.github.GHWorkflowRun;

public enum RunConclusion {
    SUCCESS,
    FAILURE,
    CANCELLED,
    SKIPPED,
    NEUTRAL,
    TIMED_OUT,
    ACTION_REQUIRED,
    STALE,
    STARTUP_FAILURE;

    GHWorkflowRun.Conclusion toSdk() {
        return GHWorkflowRun.Conclusion.valueOf(name());
    }
}
