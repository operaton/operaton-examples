package org.operaton.examples.bpmnmodelapiparse;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("approveExpenseDelegate")
public class ApproveExpenseDelegate implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        Double amount = (Double) execution.getVariable("amount");
        execution.setVariable("validated", amount != null && amount > 0);
    }
}
