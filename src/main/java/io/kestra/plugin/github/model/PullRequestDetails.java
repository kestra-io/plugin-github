package io.kestra.plugin.github.model;

import lombok.Getter;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHPerson;
import org.kohsuke.github.GHPullRequest;

import java.io.IOException;
import java.net.URL;
import java.util.Date;
import java.util.Optional;

@Getter
public class PullRequestDetails {

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

    private final String base;

    private final String head;

    private final URL url;

    public PullRequestDetails(GHPullRequest pullRequest) throws IOException {
        this.number = pullRequest.getNumber();
        this.title = pullRequest.getTitle();
        this.state = pullRequest.getState().toString();
        this.stateReason = pullRequest.getStateReason().toString();
        this.owner = pullRequest.getUser().getLogin();
        this.assignee = Optional.ofNullable(pullRequest.getAssignee()).map(GHPerson::getLogin).orElse(null);
        this.assignees = pullRequest.getAssignees().stream().map(GHPerson::getLogin).toArray();
        this.createdAt = pullRequest.getCreatedAt();
        this.closedAt = pullRequest.getClosedAt();
        this.closedBy = Optional.ofNullable(pullRequest.getClosedBy()).map(GHPerson::getLogin).orElse(null);
        this.comments = pullRequest.getCommentsCount();
        this.labels = pullRequest.getLabels().stream().map(GHLabel::getName).toArray();
        this.repositoryName = pullRequest.getRepository().getName();
        this.repositoryUrl = pullRequest.getRepository().getHtmlUrl();
        this.base = pullRequest.getBase().getRef();
        this.head = pullRequest.getHead().getRef();
        this.url = pullRequest.getHtmlUrl();
    }

}
