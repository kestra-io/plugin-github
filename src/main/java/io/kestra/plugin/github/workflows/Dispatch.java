package io.kestra.plugin.github.workflows;

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
    title = "Trigger a GitHub Action workflow manually.",
    description = "Trigger a GitHub Action workflow by creating a workflow dispatch event."
)
@Plugin(
    examples = {
        @Example(
            title = "Trigger a GitHub Action workflow manually.",
            full = true,
            code = """
                id: github_workflow_dispatch_flow
                namespace: company.team

                tasks:
                  - id: dispatch_workflow
                    type: io.kestra.plugin.github.workflows.Dispatch
                    oauthToken: your_github_token
                    repository: your_owner/your_repository
                    workflowId: your_workflow_id
                    ref: your_branch_or_tag_name
                    inputs:
                      foo:bar
                """
        )
    }
)
public class Dispatch extends GithubConnector implements RunnableTask<VoidOutput> {

    @Schema(
        title = "Repository where the workflow will be dispatched.",
        description = "The repository on which the workflow with be dispatched. Repository name must be in format owner/repo."
    )
    private Property<String> repository;

    @Schema(
        title = "Workflow id to dispatch.",
        description = "The workflow id to be dispatched."
    )
    private Property<String> workflowId;

    @Schema(
        title = "Some reference name in the repository where the dispatch will occur.",
        description = "Can be a branch or a tag."
    )
    private Property<String> ref;

    @Schema(
        title = "Optional inputs to be used for the workflow dispatch.",
        description = "Map with optional inputs.",
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
