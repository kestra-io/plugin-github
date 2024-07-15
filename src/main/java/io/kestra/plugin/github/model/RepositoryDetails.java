package io.kestra.plugin.github.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHRepository;

import java.io.IOException;
import java.net.URL;
import java.util.Date;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class RepositoryDetails {

    private final String name;

    @JsonProperty("full_name")
    private final String fullName;

    @JsonProperty("url")
    private final URL htmlUrl;

    private final String description;

    private String owner;

    @JsonProperty("task_count")
    private final int forksCount;

    @JsonProperty("stars_count")
    private final int starsCount;

    @JsonProperty("pull_request_count")
    private int pullRequestsCount;

    @JsonProperty("issues_count")
    private final int issuesCount;

    private final Date updated;

    private final Date created;

    private final String language;

    public RepositoryDetails(GHRepository repository, boolean isAnonymous) throws IOException {
        this.name = repository.getName();
        this.fullName = repository.getFullName();
        this.htmlUrl = repository.getHtmlUrl();
        this.description = repository.getDescription();
        this.forksCount = repository.getForksCount();
        this.starsCount = repository.getStargazersCount();
        this.issuesCount = repository.getOpenIssueCount();
        this.updated = repository.getUpdatedAt();
        this.created = repository.getCreatedAt();
        this.language = repository.getLanguage();

        if (!isAnonymous) {
            this.owner = repository.getOwner().getLogin();
            this.pullRequestsCount = repository.getPullRequests(GHIssueState.OPEN).size();
        }
    }

}
