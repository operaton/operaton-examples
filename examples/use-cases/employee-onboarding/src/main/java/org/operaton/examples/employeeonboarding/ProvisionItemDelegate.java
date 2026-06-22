package org.operaton.examples.employeeonboarding;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("provisionItemDelegate")
public class ProvisionItemDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(ProvisionItemDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        String item = (String) execution.getVariable("equipmentItem");
        execution.setVariable("provisioned", true);
        log.info("Provisioned equipment: item={}, employee={}",
            item, execution.getVariable("employeeId"));
    }
}
