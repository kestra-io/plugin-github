package io.kestra.plugin.github.model;

import lombok.Getter;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHPerson;

import java.io.IOException;
import java.net.URL;
import java.util.Date;
import java.util.Optional;

@Getter
public class IssueDetails {

    private final int number;

    private final String title;

    private final String state;

    private final String stateReason;

    private final String owner;

    private final String assignee;

    private final Object[] assignees;

    private final Date createdAt;

    private final Date closedAt;

    private final String closedBy;

    private final int comments;

    private final Object[] labels;

    private final String repositoryName;

    private final URL repositoryUrl;

    private final URL url;

    public IssueDetails(GHIssue issue) throws IOException {
        this.number = issue.getNumber();
        this.title = issue.getTitle();
        this.state = issue.getState().toString();
        this.stateReason = issue.getStateReason().toString();
        this.owner = issue.getUser().getLogin();
        this.assignee = Optional.ofNullable(issue.getAssignee()).map(GHPerson::getLogin).orElse(null);
        this.assignees = issue.getAssignees().stream().map(GHPerson::getLogin).toArray();
        this.createdAt = issue.getCreatedAt();
        this.closedAt = issue.getClosedAt();
        this.closedBy = Optional.ofNullable(issue.getClosedBy()).map(GHPerson::getLogin).orElse(null);
        this.comments = issue.getCommentsCount();
        this.labels = issue.getLabels().stream().map(GHLabel::getName).toArray();
        this.repositoryName = issue.getRepository().getName();
        this.repositoryUrl = issue.getRepository().getHtmlUrl();
        this.url = issue.getHtmlUrl();

    }

}
