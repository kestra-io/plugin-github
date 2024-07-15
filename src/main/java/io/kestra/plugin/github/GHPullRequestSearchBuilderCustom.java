package io.kestra.plugin.github;

import org.apache.commons.lang3.StringUtils;
import org.kohsuke.github.*;

import java.util.ArrayList;
import java.util.List;

public class GHPullRequestSearchBuilderCustom {

    private final GHPullRequestSearchBuilder searchBuilder;

    private final List<String> terms = new ArrayList<>();

    public GHPullRequestSearchBuilderCustom(GitHub gitHub) {
        this.searchBuilder = gitHub.searchPullRequests();
    }

    public GHPullRequestSearchBuilderCustom q(String qualifier, String value) {
        if (StringUtils.isEmpty(qualifier)) {
            throw new IllegalArgumentException("qualifier cannot be null or empty");
        }
        if (StringUtils.isEmpty(value)) {
            final String removeQualifier = qualifier + ":";
            terms.removeIf(term -> term.startsWith(removeQualifier));
        } else {
            terms.add(qualifier + ":" + value);
        }
        return this;
    }

    public GHPullRequestSearchBuilderCustom q(String value) {
        searchBuilder.q(value);
        return this;
    }

    public GHPullRequestSearchBuilderCustom repo(String repository) {
        q("repo", repository);
        return this;
    }

    public GHPullRequestSearchBuilderCustom author(String user) {
        q("author", user);
        return this;
    }

    public GHPullRequestSearchBuilderCustom createdByMe() {
        searchBuilder.createdByMe();
        return this;
    }

    public GHPullRequestSearchBuilderCustom assigned(String user) {
        q("assignee", user);
        return this;
    }

    public GHPullRequestSearchBuilderCustom mentions(String user) {
        q("mentions", user);
        return this;
    }

    public GHPullRequestSearchBuilderCustom isOpen() {
        searchBuilder.isOpen();
        return this;
    }

    public GHPullRequestSearchBuilderCustom isClosed() {
        searchBuilder.isClosed();
        return this;
    }

    public GHPullRequestSearchBuilderCustom isMerged() {
        searchBuilder.isMerged();
        return this;
    }

    public GHPullRequestSearchBuilderCustom isDraft() {
        searchBuilder.isDraft();
        return this;
    }

    public GHPullRequestSearchBuilderCustom head(String branch) {
        q("head", branch);
        return this;
    }

    public GHPullRequestSearchBuilderCustom base(String branch) {
        q("base", branch);
        return this;
    }

    public GHPullRequestSearchBuilderCustom commit(String sha) {
        q("SHA", sha);
        return this;
    }

    public GHPullRequestSearchBuilderCustom created(String created) {
        q("created", created);
        return this;
    }

    public GHPullRequestSearchBuilderCustom merged(String merged) {
        q("merged", merged);
        return this;
    }

    public GHPullRequestSearchBuilderCustom closed(String closed) {
        q("closed", closed);
        return this;
    }

    public GHPullRequestSearchBuilderCustom updated(String updated) {
        q("updated", updated);
        return this;
    }

    public GHPullRequestSearchBuilderCustom label(String label) {
        searchBuilder.label(label);
        return this;
    }

    public GHPullRequestSearchBuilderCustom inLabels(Iterable<String> labels) {
        searchBuilder.inLabels(labels);
        return this;
    }

    public GHPullRequestSearchBuilderCustom titleLike(String title) {
        searchBuilder.titleLike(title);
        return this;
    }

    public GHPullRequestSearchBuilderCustom order(GHDirection direction) {
        searchBuilder.order(direction);
        return this;
    }

    public GHPullRequestSearchBuilderCustom sort(GHPullRequestSearchBuilder.Sort sort) {
        searchBuilder.sort(sort);
        return this;
    }

    public PagedSearchIterable<GHPullRequest> list() {
        return searchBuilder.q(StringUtils.join(terms, " ")).list();
    }

}
