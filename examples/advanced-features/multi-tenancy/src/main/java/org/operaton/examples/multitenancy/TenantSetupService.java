package org.operaton.examples.multitenancy;

import jakarta.annotation.PostConstruct;
import org.operaton.bpm.engine.IdentityService;
import org.operaton.bpm.engine.RepositoryService;
import org.operaton.bpm.engine.identity.Group;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TenantSetupService {

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private IdentityService identityService;

    @PostConstruct
    public void setupTenants() {
        setupTenant("tenant-a", "Tenant A Corp");
        setupTenant("tenant-b", "Tenant B Ltd");
    }

    private void setupTenant(String tenantId, String tenantName) {
        // Create tenant group for legal reviewers.
        // Group IDs must satisfy the engine's resource-identifier whitelist:
        // use the tenantId with hyphens stripped, e.g. "tenantaLegal".
        String groupId = tenantId.replace("-", "") + "Legal";
        if (identityService.createGroupQuery().groupId(groupId).count() == 0) {
            Group group = identityService.newGroup(groupId);
            group.setName(tenantName + " Legal");
            identityService.saveGroup(group);
        }

        // Deploy process for this tenant
        long deploymentCount = repositoryService.createDeploymentQuery()
            .tenantIdIn(tenantId)
            .count();
        if (deploymentCount == 0) {
            repositoryService.createDeployment()
                .tenantId(tenantId)
                .addClasspathResource("contract-review.bpmn")
                .name("contract-review-" + tenantId)
                .deploy();
        }
    }
}
