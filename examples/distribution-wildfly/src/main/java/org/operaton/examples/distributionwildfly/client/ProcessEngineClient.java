package org.operaton.examples.distributionwildfly.client;

import org.operaton.bpm.engine.ProcessEngine;
import org.operaton.bpm.engine.repository.Deployment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Example Spring bean that uses the container-managed process engine without
 * being a ProcessApplication. The engine is injected via JNDI (see SpringWildflyConfig).
 */
@Component
public class ProcessEngineClient {

    @Autowired
    private ProcessEngine processEngine;

    public List<String> listDeployments() {
        return processEngine.getRepositoryService()
            .createDeploymentQuery()
            .list()
            .stream()
            .map(Deployment::getName)
            .toList();
    }
}
