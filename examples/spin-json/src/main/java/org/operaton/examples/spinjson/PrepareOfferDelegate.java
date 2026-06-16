package org.operaton.examples.spinjson;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("prepareOfferDelegate")
public class PrepareOfferDelegate implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        LoanApplication application = (LoanApplication) execution.getVariable("application");

        // Simple rate calculation: base 5% + 0.1% per year of term
        double annualRate = 5.0 + (application.getTermMonths() / 12.0 * 0.1);

        execution.setVariable("annualInterestRate", annualRate);
        execution.setVariable("monthlyPayment",
            application.getAmount() * (annualRate / 100 / 12) /
            (1 - Math.pow(1 + annualRate / 100 / 12, -application.getTermMonths())));
    }
}
