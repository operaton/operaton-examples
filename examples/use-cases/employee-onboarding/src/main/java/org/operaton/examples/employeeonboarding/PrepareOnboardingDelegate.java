package org.operaton.examples.employeeonboarding;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("prepareOnboardingDelegate")
public class PrepareOnboardingDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(PrepareOnboardingDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        if (execution.getVariable("equipmentList") == null) {
            execution.setVariable("equipmentList", List.of("laptop", "phone", "badge"));
        }
        log.info("Onboarding prepared: employee={}, equipmentList={}",
            execution.getVariable("employeeId"), execution.getVariable("equipmentList"));
    }
}
