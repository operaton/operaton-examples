package org.operaton.examples.bpmnmodelapiparse;

import org.operaton.bpm.engine.RepositoryService;
import org.operaton.bpm.engine.repository.ProcessDefinition;
import org.operaton.bpm.model.bpmn.BpmnModelInstance;
import org.operaton.bpm.model.bpmn.instance.EndEvent;
import org.operaton.bpm.model.bpmn.instance.Gateway;
import org.operaton.bpm.model.bpmn.instance.ServiceTask;
import org.operaton.bpm.model.bpmn.instance.UserTask;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ProcessModelInspector {

    private final RepositoryService repositoryService;

    public ProcessModelInspector(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    public ProcessModelReport inspectLatestDeployment(String processKey) {
        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(processKey)
                .latestVersion()
                .singleResult();

        BpmnModelInstance modelInstance = repositoryService.getBpmnModelInstance(definition.getId());

        List<String> userTaskNames = new ArrayList<>();
        for (UserTask task : modelInstance.getModelElementsByType(UserTask.class)) {
            userTaskNames.add(task.getName());
        }

        List<String> serviceTaskNames = new ArrayList<>();
        for (ServiceTask task : modelInstance.getModelElementsByType(ServiceTask.class)) {
            serviceTaskNames.add(task.getName());
        }

        int gatewayCount = modelInstance.getModelElementsByType(Gateway.class).size();

        List<String> endEventNames = new ArrayList<>();
        for (EndEvent event : modelInstance.getModelElementsByType(EndEvent.class)) {
            endEventNames.add(event.getName());
        }

        return new ProcessModelReport(
                definition.getKey(),
                definition.getName(),
                userTaskNames,
                serviceTaskNames,
                gatewayCount,
                endEventNames
        );
    }
}
