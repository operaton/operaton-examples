package org.operaton.examples.employeeonboarding;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("activateAccountDelegate")
public class ActivateAccountDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(ActivateAccountDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        log.info("Account activated: employee={}", execution.getVariable("employeeId"));
    }
}
