package io.kestra.plugin.github.actions;

import org.kohsuke.github.GHWorkflowRun;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

final class RunUtils {
    private RunUtils() {
    }

    static Instant toInstant(Date date) {
        return date == null ? null : date.toInstant();
    }

    static String lower(Enum<?> value) {
        return value == null ? null : value.name().toLowerCase();
    }

    static <E extends Enum<E>> E parseEnum(Class<E> type, String property, String value) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Invalid `" + property + "` value '" + value + "' - allowed values: " + Arrays.toString(type.getEnumConstants()).toLowerCase()
            );
        }
    }

    static Map<String, Object> toMap(GHWorkflowRun run) throws IOException {
        var url = run.getHtmlUrl();
        var map = new LinkedHashMap<String, Object>();
        map.put("runId", run.getId());
        map.put("runNumber", run.getRunNumber());
        map.put("status", lower(run.getStatus()));
        map.put("conclusion", lower(run.getConclusion()));
        map.put("event", lower(run.getEvent()));
        map.put("headBranch", run.getHeadBranch());
        map.put("headSha", run.getHeadSha());
        map.put("htmlUrl", url == null ? null : url.toString());
        map.put("createdAt", toInstant(run.getCreatedAt()));
        map.put("updatedAt", toInstant(run.getUpdatedAt()));
        return map;
    }
}