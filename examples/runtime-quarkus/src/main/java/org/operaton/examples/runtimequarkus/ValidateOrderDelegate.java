package org.operaton.examples.runtimequarkus;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;

@ApplicationScoped
@Named("validateOrderDelegate")
public class ValidateOrderDelegate implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        Double orderTotal = (Double) execution.getVariable("orderTotal");
        boolean approved = orderTotal != null && orderTotal < 1000.0;
        execution.setVariable("approved", approved);
    }
}
