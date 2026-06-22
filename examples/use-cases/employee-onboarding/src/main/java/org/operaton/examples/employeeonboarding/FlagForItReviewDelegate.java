package org.operaton.examples.employeeonboarding;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("flagForItReviewDelegate")
public class FlagForItReviewDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(FlagForItReviewDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        execution.setVariable("itReviewRequired", true);
        log.info("Flagged for IT review: employee={}, role={}",
            execution.getVariable("employeeId"), execution.getVariable("role"));
    }
}
