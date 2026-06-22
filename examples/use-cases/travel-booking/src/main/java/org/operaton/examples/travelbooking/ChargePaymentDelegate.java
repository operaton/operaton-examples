package org.operaton.examples.travelbooking;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("chargePaymentDelegate")
public class ChargePaymentDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(ChargePaymentDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        Double flightPrice = (Double) execution.getVariable("flightPrice");
        Double hotelPrice  = (Double) execution.getVariable("hotelPrice");
        Double carPrice    = (Double) execution.getVariable("carPrice");
        Double budget      = (Double) execution.getVariable("budget");

        double total = (flightPrice != null ? flightPrice : 0.0)
                     + (hotelPrice  != null ? hotelPrice  : 0.0)
                     + (carPrice    != null ? carPrice    : 0.0);
        boolean approved = budget != null && total <= budget;

        execution.setVariable("paymentApproved", approved);
        log.info("Payment charge: total={}, budget={}, approved={}", total, budget, approved);
    }
}
