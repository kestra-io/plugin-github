@PluginSubGroup(
    title = "Authentication",
    description = "This sub-group of plugins contains tasks for issuing GitHub access credentials, such as exchanging a GitHub App's private key for an installation access token.",
    categories = {
        PluginSubGroup.PluginCategory.INFRASTRUCTURE,
        PluginSubGroup.PluginCategory.BUSINESS
    }
)
package io.kestra.plugin.github.auth;

import io.kestra.core.models.annotations.PluginSubGroup;
