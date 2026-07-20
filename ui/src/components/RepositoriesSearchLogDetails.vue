<script setup lang="ts">
/**
 * LogDetails starter component
 *
 * Rendered in the log detail panel for a specific task attempt.
 * Props are injected by the Kestra runtime.
 *
 * The Props interface is imported from @kestra-io/artifact-sdk — update the
 * SDK dependency to pick up any prop changes without re-scaffolding.
 */
import type { LogDetailsProps } from "@kestra-io/artifact-sdk";

const props = defineProps<LogDetailsProps>();

const levelColor: Record<string, string> = {
  TRACE: "var(--ks-color-text-secondary, #9ca3af)",
  DEBUG: "var(--ks-color-info, #3b82f6)",
  INFO: "var(--ks-color-success, #22c55e)",
  WARN: "var(--ks-color-warning, #f59e0b)",
  ERROR: "var(--ks-color-danger, #ef4444)",
};
</script>

<template>
  <div class="log-details">
    <h3 class="log-details__title">Log Details — attempt {{ props.attemptNumber + 1 }}</h3>

    <div v-if="!props.logs?.length" class="log-details__empty">
      No log entries for this attempt.
    </div>

    <ul v-else class="log-details__list">
      <li v-for="(entry, i) in props.logs" :key="i" class="log-details__entry">
        <span class="log-details__level" :style="{ color: levelColor[entry.level] ?? 'inherit' }">{{
          entry.level
        }}</span>
        <span class="log-details__ts">{{ entry.timestamp }}</span>
        <span class="log-details__msg">{{ entry.message }}</span>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.log-details {
  padding: 1rem;
  font-size: 0.875rem;
}

.log-details__title {
  margin: 0 0 0.75rem;
  font-size: 1rem;
  font-weight: 600;
}

.log-details__empty {
  color: var(--ks-color-text-secondary, #6b7280);
  font-style: italic;
}

.log-details__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.log-details__entry {
  display: grid;
  grid-template-columns: 5rem 11rem 1fr;
  gap: 0.5rem;
  font-family: monospace;
  font-size: 0.8rem;
}

.log-details__level {
  font-weight: 700;
}

.log-details__ts {
  color: var(--ks-color-text-secondary, #9ca3af);
}
</style>
