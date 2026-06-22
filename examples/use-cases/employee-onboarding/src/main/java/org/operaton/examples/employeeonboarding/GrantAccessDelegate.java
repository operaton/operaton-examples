package org.operaton.examples.employeeonboarding;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("grantAccessDelegate")
public class GrantAccessDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(GrantAccessDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        String role = (String) execution.getVariable("role");
        boolean granted = !"restricted".equals(role);
        execution.setVariable("accessGranted", granted);
        log.info("System access {}: employee={}, role={}",
            granted ? "granted" : "denied", execution.getVariable("employeeId"), role);
    }
}
