package org.operaton.examples.spinjson;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("validateApplicationDelegate")
public class ValidateApplicationDelegate implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        // Read the JSON-serialized LoanApplication variable back as a typed Java object
        LoanApplication application = (LoanApplication) execution.getVariable("application");

        boolean valid = application.getAmount() > 0
                && application.getTermMonths() > 0
                && application.getApplicantName() != null && !application.getApplicantName().isBlank();

        execution.setVariable("applicationValid", valid);
    }
}
