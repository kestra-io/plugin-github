package io.kestra.plugin.github.actions;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.github.AbstractGithubClientTest;
import io.kestra.plugin.github.MockController;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
public class TriggerTest extends AbstractGithubClientTest {
    @Inject
    private RunContextFactory runContextFactory;

    private Trigger.TriggerBuilder<?, ?> trigger() {
        return Trigger.builder()
            .id("watch-runs")
            .type(Trigger.class.getName())
            .oauthToken(Property.ofValue(""))
            .endpoint(Property.ofValue(embeddedServer.getURI().toString()))
            .repository(Property.ofValue("kestra-io/mock-kestra"))
            .interval(Duration.ofMinutes(1));
    }

    private static TriggerContext triggerContext(String flowId, ZonedDateTime date) {
        return TriggerContext.builder()
            .tenantId(null)
            .namespace("company.team")
            .flowId(flowId)
            .triggerId("watch-runs")
            .date(date)
            .build();
    }

    private static String randomFlowId() {
        return "test-flow-" + UUID.randomUUID();
    }

    private static String secondsAgo(long seconds) {
        return Instant.now().minusSeconds(seconds).truncatedTo(ChronoUnit.SECONDS).toString();
    }

    @Test
    void firesWhenRunUpdatedAfterWatermark() throws Exception {
        var trigger = trigger().build();
        var conditionContext = TestsUtils.mockTrigger(runContextFactory, trigger).getKey();

        // the mocked run was last updated in 2024, so a 2023 watermark must see it
        var result = trigger.evaluate(conditionContext, triggerContext(randomFlowId(), ZonedDateTime.parse("2023-01-01T00:00:00Z")));

        assertThat(result).isPresent();
    }

    @Test
    void staysSilentWhenNothingNewSinceWatermark() throws Exception {
        var trigger = trigger().build();
        var conditionContext = TestsUtils.mockTrigger(runContextFactory, trigger).getKey();

        var result = trigger.evaluate(conditionContext, triggerContext(randomFlowId(), ZonedDateTime.now()));

        assertThat(result).isEmpty();
    }

    @Test
    void doesNotFireTwiceForTheSameRun() throws Exception {
        var trigger = trigger().build();
        var conditionContext = TestsUtils.mockTrigger(runContextFactory, trigger).getKey();
        var context = triggerContext(randomFlowId(), ZonedDateTime.parse("2023-01-01T00:00:00Z"));

        var first = trigger.evaluate(conditionContext, context);
        var second = trigger.evaluate(conditionContext, context);

        assertThat(first).isPresent();
        assertThat(second).isEmpty();
    }

    @Test
    void doesNotDeliverARecentRunTwice() throws Exception {
        MockController.runUpdatedAt = secondsAgo(10);
        var trigger = trigger().build();
        var conditionContext = TestsUtils.mockTrigger(runContextFactory, trigger).getKey();
        var context = triggerContext(randomFlowId(), ZonedDateTime.now().minusMinutes(1));

        var first = trigger.evaluate(conditionContext, context);
        var second = trigger.evaluate(conditionContext, context);

        assertThat(first).isPresent();
        assertThat(second).isEmpty();
    }

    @Test
    void deliversARunListedLateWithinTheLookback() throws Exception {
        // updated 2 minutes ago, but the watermark is already "now": only the lookback window can still see it
        MockController.runUpdatedAt = secondsAgo(120);
        var trigger = trigger().build();
        var conditionContext = TestsUtils.mockTrigger(runContextFactory, trigger).getKey();

        var result = trigger.evaluate(conditionContext, triggerContext(randomFlowId(), ZonedDateTime.now()));

        assertThat(result).isPresent();
    }

    @Test
    void storesTheStateAsAMap() throws Exception {
        var flowId = randomFlowId();
        var trigger = trigger().build();
        var conditionContext = TestsUtils.mockTrigger(runContextFactory, trigger).getKey();

        trigger.evaluate(conditionContext, triggerContext(flowId, ZonedDateTime.parse("2023-01-01T00:00:00Z")));

        var runContext = conditionContext.getRunContext();
        var key = "github_actions_watermark_" + flowId.length() + "_" + flowId + "_watch-runs";
        var stored = runContext.namespaceKv(runContext.flowInfo().namespace()).getValue(key);

        assertThat(stored).isPresent();
        assertThat(stored.get().value()).isInstanceOf(Map.class);
        var state = (Map<?, ?>) stored.get().value();
        assertThat(state.containsKey("watermark")).isTrue();
        assertThat(state.containsKey("delivered")).isTrue();
    }

    @Test
    void watchesOnlyTheConfiguredWorkflow() throws Exception {
        var date = ZonedDateTime.parse("2023-01-01T00:00:00Z");

        var other = trigger().workflowId(Property.ofValue("999")).build();
        var otherResult = other.evaluate(TestsUtils.mockTrigger(runContextFactory, other).getKey(), triggerContext(randomFlowId(), date));

        var matching = trigger().workflowId(Property.ofValue("105842276")).build();
        var matchingResult = matching.evaluate(TestsUtils.mockTrigger(runContextFactory, matching).getKey(), triggerContext(randomFlowId(), date));

        assertThat(otherResult).isEmpty();
        assertThat(matchingResult).isPresent();
    }
}
