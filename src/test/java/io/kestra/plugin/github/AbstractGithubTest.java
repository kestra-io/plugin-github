package io.kestra.plugin.github;

import com.google.common.base.Strings;

public abstract class AbstractGithubTest {
    private static final String GITHUB_OAUTH_TOKEN = System.getenv("GITHUB_OAUTH_TOKEN");

    protected static boolean isTokenMissing() {
        return Strings.isNullOrEmpty(getToken());
    }

    protected static String getToken() {
        return GITHUB_OAUTH_TOKEN;
    }
}
