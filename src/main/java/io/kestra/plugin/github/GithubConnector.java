package io.kestra.plugin.github;

import io.kestra.core.runners.RunContext;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

@Getter
@SuperBuilder
@ToString
@EqualsAndHashCode
@NoArgsConstructor
public abstract class GithubConnector extends GithubConnection {

    protected GitHub connect(final RunContext runContext) throws Exception {
        final GithubClientConfig clientConfig = getClientConfig(runContext);
        return connect(clientConfig);
    }

    private GitHub connect(GithubClientConfig config) throws Exception {
        if (config.login() != null && config.oauthToken() != null) {
	        return GitHub.connect(config.login(), config.oauthToken());
        }

        if (config.oauthToken() != null) {
	        return GitHub.connectUsingOAuth(config.oauthToken());
        }

        if (config.jwtToken() != null) {
            return new GitHubBuilder().withJwtToken(config.jwtToken()).build();
        }

        return GitHub.connectAnonymously();
    }

}
