package io.kestra.plugin.github;

import io.kestra.core.models.tasks.Task;
import jakarta.annotation.Nullable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class GithubConnection extends Task implements GithubConnectionInterface {

    private String login;

    private String oauthToken;

    private String jwtToken;

    public record GithubClientConfig(
        @Nullable String login,
        @Nullable String oauthToken,
        @Nullable String jwtToken
    ) {
    }

}
