package org.operaton.examples.procurementcollaboration;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("calculateQuoteDelegate")
public class CalculateQuoteDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(CalculateQuoteDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        String item = (String) execution.getVariable("item");
        Integer quantity = (Integer) execution.getVariable("quantity");
        int unitPrice = 100;
        int totalPrice = unitPrice * (quantity != null ? quantity : 1);
        execution.setVariable("unitPrice", unitPrice);
        execution.setVariable("totalPrice", totalPrice);
        log.info("Calculated quote: item={}, quantity={}, unitPrice={}, totalPrice={}",
            item, quantity, unitPrice, totalPrice);
    }
}
