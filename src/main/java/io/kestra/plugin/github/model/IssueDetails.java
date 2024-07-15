package io.kestra.plugin.github.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueStateReason;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHPerson;

import java.io.IOException;
import java.net.URL;
import java.util.Date;
import java.util.Optional;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class IssueDetails {

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

    private final URL url;

    public IssueDetails(GHIssue issue, boolean isAnonymous) throws IOException {
        this.number = issue.getNumber();
        this.title = issue.getTitle();
        this.state = issue.getState().toString();
        this.stateReason = Optional.ofNullable(issue.getStateReason()).map(GHIssueStateReason::toString).orElse(null);
        this.owner = issue.getUser().getLogin();
        this.assignee = Optional.ofNullable(issue.getAssignee()).map(GHPerson::getLogin).orElse(null);
        this.assignees = issue.getAssignees().stream().map(GHPerson::getLogin).toArray();
        this.createdAt = issue.getCreatedAt();
        this.closedAt = issue.getClosedAt();
        this.closedBy = Optional.ofNullable(issue.getClosedBy()).map(GHPerson::getLogin).orElse(null);
        this.comments = issue.getCommentsCount();
        this.labels = issue.getLabels().stream().map(GHLabel::getName).toArray();
        this.url = issue.getHtmlUrl();

        if (!isAnonymous) {
            this.repositoryName = issue.getRepository().getName();
            this.repositoryUrl = issue.getRepository().getHtmlUrl();
        }

    }

}
