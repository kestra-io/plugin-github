package io.kestra.plugin.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.github.GHObject;
import org.kohsuke.github.GitHub;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class GHTopicSearchBuilder {

    public static final String ACCEPT_HEADER = "application/vnd.github+json";

    public static final String API_VERSION_HEADER = "2022-11-28";

    private final GitHub root;

    private final String oauthToken;

    private final List<String> terms = new ArrayList<>();

    private final List<String> parameters = new ArrayList<>();

    private Map<String, Object> items;

    public GHTopicSearchBuilder(GitHub gitHub, String oauthToken) {
        this.root = gitHub;
        this.oauthToken = oauthToken;
    }

    public GHTopicSearchBuilder query(String query) {
        terms.add(query);
        return this;
    }

    public GHTopicSearchBuilder is(String value) {
        return query("is:" + value);
    }

    public GHTopicSearchBuilder repositories(String value) {
        return query("repositories:" + value);
    }

    public GHTopicSearchBuilder created(String value) {
        return query("created:" + value);
    }

    public GHTopicSearchBuilder order(String value) {
        parameters.add("order=" + value);
        return this;
    }

    private String getApiUrl() {
        return root.getApiUrl() + "/search/topics";
    }

    private String getUrlWithQuery() {
        String url = getApiUrl() + "?q=" + StringUtils.join(terms, " ");

        if (this.parameters.isEmpty()) {
            url += "&" + StringUtils.join(parameters, "&");
        }

        return url;
    }

    public GHTopicResponse list() throws Exception {
        URL url = new URL(getUrlWithQuery().replaceAll(" ", "+"));

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        if (this.oauthToken != null) {
            connection.setRequestProperty("Authorization", "token " + oauthToken);
        }

        connection.setRequestProperty("Accept", ACCEPT_HEADER);
        connection.setRequestProperty("X-GitHub-Api-Version", API_VERSION_HEADER);

        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new RuntimeException("HTTP GET Request Failed with Error code : " + responseCode);
        }

        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        String inputLine;
        StringBuilder response = new StringBuilder();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();
        connection.disconnect();

        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(response.toString(), GHTopicResponse.class);
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GHTopicResponse {

        @JsonProperty("total_count")
        private int totalCount;

        @JsonProperty("incomplete_results")
        private boolean incompleteResults;

        @JsonProperty("items")
        private List<GHTopic> items;

        @JsonProperty("url")
        private URL htmlUrl;

        @Data
        public static class GHTopic {
            private String name;

            @JsonProperty("display_name")
            private String displayName;

            @JsonProperty("short_description")
            private String shortDescription;

            private String description;

            @JsonProperty("created_by")
            private String createdBy;

            private String released;

            @JsonProperty("created_at")
            private String createdAt;

            @JsonProperty("updated_at")
            private String updatedAt;

            private boolean featured;

            private boolean curated;

            private int score;

        }

    }

}
