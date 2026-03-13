package io.kestra.plugin.github.actions;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.github.AbstractGithubTask;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Dispatch a workflow run",
    description = "Triggers a `workflow_dispatch` event for a workflow in the target repository. The authenticated token must be allowed to read the repository and run workflows on the selected ref."
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
public class RunWorkflow extends AbstractGithubTask implements RunnableTask<VoidOutput> {
    @Schema(
        title = "Target repository",
        description = "Repository in `owner/repo` format containing the workflow. The authenticated token must have access to this repository."
    )
    private Property<String> repository;

    @Schema(
        title = "Workflow ID or filename",
        description = "Workflow identifier accepted by the GitHub API such as a numeric ID or a workflow filename like `build.yml`"
    )
    private Property<String> workflowId;

    @Schema(
        title = "Workflow ref",
        description = "Branch or tag name used to resolve the workflow file. This ref must exist in the target repository."
    )
    private Property<String> ref;

    @Schema(
        title = "Workflow inputs map",
        description = "Key/value payload passed to workflow `inputs`. Property values are rendered before the dispatch request is sent",
        requiredMode = Schema.RequiredMode.NOT_REQUIRED
    )
    private Property<Map<String, Object>> inputs;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        var gitHub = connect(runContext);

        var repo = gitHub.getRepository(runContext.render(repository).as(String.class).orElse(null));
        var workflow = repo.getWorkflow(runContext.render(workflowId).as(String.class).orElse(null));

        workflow.dispatch(
            runContext.render(ref).as(String.class).orElse(null),
            runContext.render(inputs).asMap(String.class, Object.class)
        );

        return null;
    }
}
