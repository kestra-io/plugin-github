import type { Meta, StoryObj } from "@storybook/vue3";
import RepositoriesSearchTopologyDetails from "./components/RepositoriesSearchTopologyDetails.vue";
import { SLOTS } from "@kestra-io/artifact-sdk";

const meta: Meta<typeof RepositoriesSearchTopologyDetails> = {
  title: "Plugin UI / topology-details / RepositoriesSearchTopologyDetails",
  component: RepositoriesSearchTopologyDetails,
  tags: ["autodocs"],
  argTypes: {
    // TODO: define argTypes matching your component's props
  },
};

export default meta;
type Story = StoryObj<typeof RepositoriesSearchTopologyDetails>;

export const Default: Story = {
  args: SLOTS["topology-details"].defaultProps,
};
