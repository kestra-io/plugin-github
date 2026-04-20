import defaultViteConfig from "@kestra-io/artifact-sdk/vite.config";

export default defaultViteConfig({
  plugin: "io.kestra.plugin.github",
  
  exposes: {
    "repositories.Search": [
      {
        slotName: "log-details",
        path: "./src/components/RepositoriesSearchLogDetails.vue",
      },
    ],
  },
  
});
