package io.kestra.plugin.github.actions;

public enum RunEvent {
    PUSH,
    PULL_REQUEST,
    PULL_REQUEST_TARGET,
    WORKFLOW_DISPATCH,
    WORKFLOW_RUN,
    SCHEDULE,
    RELEASE,
    REPOSITORY_DISPATCH,
    ISSUES,
    ISSUE_COMMENT,
    MERGE_GROUP,
    CREATE,
    DELETE,
    FORK;

    String value() {
        return name().toLowerCase();
    }
}
