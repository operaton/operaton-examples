package org.operaton.examples.runtimequarkus;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.operaton.bpm.engine.RepositoryService;
import org.operaton.bpm.quarkus.engine.extension.event.OperatonEngineStartupEvent;

@ApplicationScoped
public class ProcessDeployment {

    @Inject
    RepositoryService repositoryService;

    public void onEngineStart(@Observes OperatonEngineStartupEvent event) {
        repositoryService.createDeployment()
            .addClasspathResource("order-approval.bpmn")
            .enableDuplicateFiltering(false)
            .deploy();
    }
}
