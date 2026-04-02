package io.kestra.plugin.github.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.kestra.plugin.github.topics.Search;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class TopicDetails {

    private final String name;

    @JsonProperty("display_name")
    private final String displayName;

    @JsonProperty("short_description")
    private final String shortDescription;

    private final String description;

    @JsonProperty("created_by")
    private final String createdBy;

    private final String released;

    @JsonProperty("created_at")
    private final String createdAt;

    @JsonProperty("updated_at")
    private final String updatedAt;

    private final boolean featured;

    private final boolean curated;

    private final int score;

    public TopicDetails(Search.GHTopicSearchBuilder.GHTopicResponse.GHTopic topic) {
        this.name = topic.getName();
        this.displayName = topic.getDisplayName();
        this.shortDescription = topic.getShortDescription();
        this.description = topic.getDescription();
        this.createdBy = topic.getCreatedBy();
        this.released = topic.getReleased();
        this.createdAt = topic.getCreatedAt();
        this.updatedAt = topic.getUpdatedAt();
        this.featured = topic.isFeatured();
        this.curated = topic.isCurated();
        this.score = topic.getScore();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();

        map.put("name", name);
        map.put("display_name", displayName);
        map.put("short_description", shortDescription);
        map.put("description", description);
        map.put("created_by", createdBy);
        map.put("released", released);
        map.put("created_at", createdAt);
        map.put("updated_at", updatedAt);
        map.put("featured", featured);
        map.put("curated", curated);
        map.put("score", score);

        return map;
    }
}