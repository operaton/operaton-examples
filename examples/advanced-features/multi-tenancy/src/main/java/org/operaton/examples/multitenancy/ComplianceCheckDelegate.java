package org.operaton.examples.multitenancy;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("complianceCheckDelegate")
public class ComplianceCheckDelegate implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        String tenantId = execution.getTenantId();
        String contractType = (String) execution.getVariable("contractType");

        // Simulate tenant-specific compliance rules
        boolean compliant;
        if ("tenant-a".equals(tenantId)) {
            // Tenant A: strict — only "standard" contracts are compliant
            compliant = "standard".equals(contractType);
        } else {
            // All other tenants: both "standard" and "enterprise" are compliant
            compliant = "standard".equals(contractType) || "enterprise".equals(contractType);
        }

        execution.setVariable("compliant", compliant);
        // Set tenantGroup variable for the dynamic candidateGroup expression ${tenantGroup}Legal.
        // Group IDs must not contain hyphens, so we strip them from the tenant ID.
        execution.setVariable("tenantGroup", tenantId.replace("-", ""));
    }
}
