package io.kestra.plugin.github.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import org.kohsuke.github.GHCommitPointer;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHPerson;
import org.kohsuke.github.GHPullRequest;

import java.io.IOException;
import java.net.URL;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class PullRequestDetails {

    private final int number;

    private final String title;

    private final String state;

    @JsonProperty("state_reason")
    private final String stateReason;

    private final String owner;

    private final String assignee;

    private final Object[] assignees;

    @JsonProperty("created_at")
    private final Date createdAt;

    @JsonProperty("closed_at")
    private final Date closedAt;

    @JsonProperty("closed_by")
    private final String closedBy;

    private final int comments;

    private final Object[] labels;

    @JsonProperty("repository_name")
    private String repositoryName;

    @JsonProperty("repository_url")
    private URL repositoryUrl;

    private final String base;

    private final String head;

    private final URL url;

    public PullRequestDetails(GHPullRequest pullRequest, boolean isAnonymous) throws IOException {
        this.number = pullRequest.getNumber();
        this.title = pullRequest.getTitle();
        this.state = pullRequest.getState().toString();
        this.stateReason = Optional.ofNullable(pullRequest.getStateReason()).map(Object::toString).orElse(null);
        this.owner = pullRequest.getUser().getLogin();
        this.assignee = Optional.ofNullable(pullRequest.getAssignee()).map(GHPerson::getLogin).orElse(null);
        this.assignees = pullRequest.getAssignees().stream().map(GHPerson::getLogin).toArray();
        this.createdAt = pullRequest.getCreatedAt();
        this.closedAt = pullRequest.getClosedAt();
        this.closedBy = Optional.ofNullable(pullRequest.getClosedBy()).map(GHPerson::getLogin).orElse(null);
        this.comments = pullRequest.getCommentsCount();
        this.labels = pullRequest.getLabels().stream().map(GHLabel::getName).toArray();
        this.base = Optional.ofNullable(pullRequest.getBase()).map(GHCommitPointer::getRef).orElse(null);
        this.head = Optional.ofNullable(pullRequest.getHead()).map(GHCommitPointer::getRef).orElse(null);
        this.url = pullRequest.getHtmlUrl();

        if (!isAnonymous) {
            this.repositoryName = pullRequest.getRepository().getName();
            this.repositoryUrl = pullRequest.getRepository().getHtmlUrl();
        }
    }

}
