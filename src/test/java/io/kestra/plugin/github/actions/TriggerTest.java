package io.kestra.plugin.github.actions;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.github.AbstractGithubClientTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
public class TriggerTest extends AbstractGithubClientTest {
    @Inject
    private RunContextFactory runContextFactory;

    private Trigger trigger() {
        return Trigger.builder()
            .id("watch-runs")
            .type(Trigger.class.getName())
            .oauthToken(Property.ofValue(""))
            .endpoint(Property.ofValue(embeddedServer.getURI().toString()))
            .repository(Property.ofValue("kestra-io/mock-kestra"))
            .interval(Duration.ofMinutes(1))
            .build();
    }

    private static TriggerContext triggerContext(ZonedDateTime date) {
        return TriggerContext.builder()
            .tenantId(null)
            .namespace("company.team")
            .flowId("test-flow")
            .triggerId("watch-runs")
            .date(date)
            .build();
    }

    @Test
    void firesWhenRunUpdatedAfterWatermark() throws Exception {
        var trigger = trigger();
        var conditionContext = TestsUtils.mockTrigger(runContextFactory, trigger).getKey();

        // the mocked run was last updated in 2024, so a 2023 watermark must see it
        var result = trigger.evaluate(conditionContext, triggerContext(ZonedDateTime.parse("2023-01-01T00:00:00Z")));

        assertThat(result).isPresent();
    }

    @Test
    void staysSilentWhenNothingNewSinceWatermark() throws Exception {
        var trigger = trigger();
        var conditionContext = TestsUtils.mockTrigger(runContextFactory, trigger).getKey();

        var result = trigger.evaluate(conditionContext, triggerContext(ZonedDateTime.now()));

        assertThat(result).isEmpty();
    }
}