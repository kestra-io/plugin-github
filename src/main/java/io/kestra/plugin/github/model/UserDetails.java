package io.kestra.plugin.github.model;

import lombok.Getter;
import org.kohsuke.github.GHUser;

import java.io.IOException;
import java.net.URL;
import java.util.Date;

@Getter
public class UserDetails {

    private final String username;

    private final String name;

    private final int followers;

    private final int following;

    private final String location;

    private final String company;

    private final int publicRepositories;

    private final Integer privateRepositories;

    private final Date updated;

    private final Date created;

    private final URL url;

    public UserDetails(GHUser user) throws IOException {
        this.username = user.getLogin();
        this.name = user.getName();
        this.followers = user.getFollowersCount();
        this.following = user.getFollowingCount();
        this.location = user.getLocation();
        this.company = user.getCompany();
        this.publicRepositories = user.getPublicRepoCount();
        this.privateRepositories = user.getTotalPrivateRepoCount().orElse(null);
        this.updated = user.getUpdatedAt();
        this.created = user.getCreatedAt();
        this.url = user.getHtmlUrl();
    }

}
