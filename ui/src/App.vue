<script setup lang="ts">
import RepositoriesSearchLogDetails from "./components/RepositoriesSearchLogDetails.vue";
import { SLOTS } from "@kestra-io/artifact-sdk";
</script>

<template>
  <div style="padding: 1rem; font-family: sans-serif">
    <RepositoriesSearchLogDetails v-bind="SLOTS['log-details'].defaultProps" />
  </div>
</template>
