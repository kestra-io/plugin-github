package io.kestra.plugin.github.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import org.kohsuke.github.GHUser;

import java.io.IOException;
import java.net.URL;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserDetails {

    private final String username;

    private final URL url;

    private String name;

    private int followers;

    private int following;

    private String location;

    private String company;

    @JsonProperty("public_repositories")
    private int publicRepositories;

    @JsonProperty("private_repositories")
    private Integer privateRepositories;

    private Date updated;

    private Date created;

    private String type;

    public UserDetails(GHUser user, boolean isAnonymous) throws IOException {
        this.username = user.getLogin();
        this.url = user.getHtmlUrl();

        if (!isAnonymous) {
            this.name = user.getName();
            this.company = user.getCompany();
            this.location = user.getLocation();
            this.created = user.getCreatedAt();
            this.updated = user.getUpdatedAt();
            this.publicRepositories = user.getPublicRepoCount();
            this.privateRepositories = user.getTotalPrivateRepoCount().orElse(null);
            this.followers = user.getFollowersCount();
            this.following = user.getFollowingCount();
            this.type = user.getType();
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();

        map.put("username", username);
        map.put("url", url);
        map.put("name", name);
        map.put("followers", followers);
        map.put("following", following);
        map.put("location", location);
        map.put("company", company);
        map.put("public_repositories", publicRepositories);
        map.put("private_repositories", privateRepositories);
        map.put("updated", updated);
        map.put("created", created);
        map.put("type", type);

        return map;
    }
}
