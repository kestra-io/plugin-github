package io.kestra.plugin.github.actions;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.github.GithubConnector;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Dispatch a GitHub Actions workflow",
    description = "Creates a workflow_dispatch event on the target repository. Requires an OAuth/JWT token with permission to run workflows; uses the provided ref (branch or tag) and optional inputs."
)
@Plugin(
    examples = {
        @Example(
            title = "Trigger a GitHub Action workflow manually.",
            full = true,
            code = """
                id: run_github_action_workflow_flow
                namespace: company.team

                tasks:
                  - id: run_workflow
                    type: io.kestra.plugin.github.actions.RunWorkflow
                    oauthToken: "{{ secret('GITHUB_ACCESS_TOKEN') }}"
                    repository: your_owner/your_repository
                    workflowId: your_workflow_id
                    ref: your_branch_or_tag_name
                    inputs:
                      foo:bar
                """
        )
    }
)
public class RunWorkflow extends GithubConnector implements RunnableTask<VoidOutput> {

    @Schema(
        title = "Repository to dispatch in",
        description = "`owner/repo` where the workflow file lives; token must have access there."
    )
    private Property<String> repository;

    @Schema(
        title = "Workflow id or filename",
        description = "Workflow identifier accepted by GitHub API (numeric id or `workflow.yml` filename)."
    )
    private Property<String> workflowId;

    @Schema(
        title = "Branch or tag to run on",
        description = "Ref name where GitHub will resolve the workflow; must exist in the repository."
    )
    private Property<String> ref;

    @Schema(
        title = "Workflow inputs map",
        description = "Key/value payload passed to workflow `inputs`; optional, defaults to none.",
        requiredMode = Schema.RequiredMode.NOT_REQUIRED
    )
    private Property<Map<String, Object>> inputs;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        var gitHub = connect(runContext);
        var repo = gitHub.getRepository(runContext.render(repository).as(String.class).orElse(null));
        var workflow = repo.getWorkflow(runContext.render(workflowId).as(String.class).orElse(null));
        workflow.dispatch(runContext.render(ref).as(String.class).orElse(null), runContext.render(inputs).asMap(String.class, Object.class));
        return null;
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
    }
}
