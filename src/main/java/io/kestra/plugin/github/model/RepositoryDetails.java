package io.kestra.plugin.github.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHRepository;

import java.io.IOException;
import java.net.URL;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

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

    private final boolean archived;

    private final boolean fork;

    private final boolean disabled;

    private final boolean template;

    @JsonProperty("default_branch")
    private final String defaultBranch;

    private final String visibility;

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
        this.archived = repository.isArchived();
        this.fork = repository.isFork();
        this.disabled = repository.isDisabled();
        this.template = repository.isTemplate();
        this.defaultBranch = repository.getDefaultBranch();
        this.visibility = repository.getVisibility().name();

        if (!isAnonymous) {
            this.owner = repository.getOwner().getLogin();
            this.pullRequestsCount = repository.getPullRequests(GHIssueState.OPEN).size();
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();

        map.put("name", name);
        map.put("full_name", fullName);
        map.put("url", htmlUrl);
        map.put("description", description);
        map.put("owner", owner);
        map.put("forks_count", forksCount);
        map.put("stars_count", starsCount);
        map.put("pull_request_count", pullRequestsCount);
        map.put("issues_count", issuesCount);
        map.put("updated", updated);
        map.put("created", created);
        map.put("language", language);
        map.put("archived", archived);
        map.put("fork", fork);
        map.put("disabled", disabled);
        map.put("template", template);
        map.put("default_branch", defaultBranch);
        map.put("visibility", visibility);

        return map;
    }

}
