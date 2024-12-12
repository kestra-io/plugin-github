package io.kestra.plugin.github;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;

public interface GithubConnectionInterface {

    @Schema(
        title = "GitHub login",
        description = "Requires additional field: oauthToken, to log-in"
    )
    Property<String> getLogin();

    @Schema(
        title = "GitHub oauthToken",
        description = "GitHub Personal Access Token. In addition, can be used with login or by its own"
    )
    Property<String> getOauthToken();

    @Schema(
        title = "GitHub JWT token",
        description = "Does not requires additional fields to log-in"
    )
    Property<String> getJwtToken();

    @Schema(
        title = "GitHub repository",
        description = "Repository where issue/ticket should be created. It's a string of Username + / + Repository name",
        example = "kestra-io/plugin-github"
    )
    default Property<String> getRepository() {
        return null;
    }

    default GithubConnection.GithubClientConfig getClientConfig(RunContext runContext) throws IllegalVariableEvaluationException {
        return new GithubConnection.GithubClientConfig(
            runContext.render(this.getLogin()).as(String.class).orElse(null),
            runContext.render(this.getOauthToken()).as(String.class).orElse(null),
            runContext.render(this.getJwtToken()).as(String.class).orElse(null)
        );
    }

}
