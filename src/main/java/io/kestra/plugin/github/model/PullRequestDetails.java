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
import java.util.*;

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

    @JsonProperty("requested_reviewers")
    private final List<String> requestedReviewers;

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
        this.requestedReviewers = pullRequest.getRequestedReviewers().stream().map(GHPerson::getLogin).toList();
        this.url = pullRequest.getHtmlUrl();

        if (!isAnonymous) {
            this.repositoryName = pullRequest.getRepository().getName();
            this.repositoryUrl = pullRequest.getRepository().getHtmlUrl();
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();

        map.put("number", number);
        map.put("title", title);
        map.put("state", state);
        map.put("state_reason", stateReason);
        map.put("owner", owner);
        map.put("assignee", assignee);
        map.put("assignees", assignees);
        map.put("created_at", createdAt);
        map.put("closed_at", closedAt);
        map.put("closed_by", closedBy);
        map.put("comments", comments);
        map.put("labels", labels);
        map.put("repository_name", repositoryName);
        map.put("repository_url", repositoryUrl);
        map.put("base", base);
        map.put("head", head);
        map.put("requested_reviewers", requestedReviewers);
        map.put("url", url);

        return map;
    }

}
