import type { Meta, StoryObj } from "@storybook/vue3";
import RepositoriesSearchLogDetails from "./components/RepositoriesSearchLogDetails.vue";
import { SLOTS } from "@kestra-io/artifact-sdk";

const meta: Meta<typeof RepositoriesSearchLogDetails> = {
  title: "Plugin UI / log-details / RepositoriesSearchLogDetails",
  component: RepositoriesSearchLogDetails,
  tags: ["autodocs"],
  argTypes: {
    // TODO: define argTypes matching your component's props
  },
};

export default meta;
type Story = StoryObj<typeof RepositoriesSearchLogDetails>;

export const Default: Story = {
  args: SLOTS["log-details"].defaultProps,
};
