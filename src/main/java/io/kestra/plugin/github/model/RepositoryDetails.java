package io.kestra.plugin.github.model;

import lombok.Getter;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHRepository;

import java.io.IOException;
import java.net.URL;
import java.util.Date;

@Getter
public class RepositoryDetails {

    private final String name;

    private final String fullName;

    private final URL htmlUrl;

    private final String description;

    private final String owner;

    private final int forksCount;

    private final int starsCount;

    private final int pullRequestsCount;

    private final int issuesCount;

    private final Date updated;

    private final Date created;

    private final String language;

    public RepositoryDetails(GHRepository repository) throws IOException {
        this.name = repository.getName();
        this.fullName = repository.getFullName();
        this.htmlUrl = repository.getHtmlUrl();
        this.description = repository.getDescription();
        this.owner = repository.getOwner().getLogin();
        this.forksCount = repository.getForksCount();
        this.starsCount = repository.getStargazersCount();
        this.pullRequestsCount = repository.getPullRequests(GHIssueState.OPEN).size();
        this.issuesCount = repository.getOpenIssueCount();
        this.updated = repository.getUpdatedAt();
        this.created = repository.getCreatedAt();
        this.language = repository.getLanguage();
    }

}
