package org.operaton.examples.signalevents.delegate;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("registerBackorderDelegate")
public class RegisterBackorderDelegate implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        String orderId = (String) execution.getVariable("orderId");
        execution.setVariable("backorderRef", "BO-" + orderId);
    }
}
