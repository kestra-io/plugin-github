<script setup lang="ts">
/**
 * TopologyDetails starter component
 *
 * This component is rendered inside the topology detail panel when a task node
 * is selected in the Kestra UI. Props are injected by the Kestra runtime.
 *
 * The Props interface is imported from @kestra-io/artifact-sdk — update the
 * SDK dependency to pick up any prop changes without re-scaffolding.
 */
import type { TopologyDetailsProps } from "@kestra-io/artifact-sdk";

const props = defineProps<TopologyDetailsProps>();
</script>

<template>
  <div class="topology-details">
    <h3 class="topology-details__title">
      {{ (props.task?.id as string) ?? "Task" }} — Topology Details
    </h3>

    <dl class="topology-details__grid">
      <dt>Task type</dt>
      <dd>{{ props.task?.type ?? "—" }}</dd>

      <dt>Namespace</dt>
      <dd>{{ props.namespace }}</dd>

      <dt>Flow</dt>
      <dd>{{ props.flowId }}</dd>
    </dl>

    <!-- TODO: replace with your custom content -->
    <pre class="topology-details__debug">{{ JSON.stringify(props.task, null, 2) }}</pre>
  </div>
</template>

<style scoped>
.topology-details {
  padding: 1rem;
  font-size: 0.875rem;
}

.topology-details__title {
  margin: 0 0 0.75rem;
  font-size: 1rem;
  font-weight: 600;
}

.topology-details__grid {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 0.25rem 1rem;
  margin: 0 0 1rem;
}

.topology-details__grid dt {
  font-weight: 500;
  color: var(--ks-color-text-secondary, #6b7280);
}

.topology-details__debug {
  background: var(--ks-color-surface-subtle, #f3f4f6);
  border-radius: 4px;
  padding: 0.5rem;
  font-size: 0.75rem;
  overflow: auto;
  max-height: 200px;
}
</style>
