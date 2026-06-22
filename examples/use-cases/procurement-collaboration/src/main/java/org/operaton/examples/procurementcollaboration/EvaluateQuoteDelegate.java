package org.operaton.examples.procurementcollaboration;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("evaluateQuoteDelegate")
public class EvaluateQuoteDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(EvaluateQuoteDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        Integer totalPrice = (Integer) execution.getVariable("totalPrice");
        Integer maxBudget = (Integer) execution.getVariable("maxBudget");
        boolean accepted = totalPrice != null && maxBudget != null && totalPrice <= maxBudget;
        execution.setVariable("accepted", accepted);
        log.info("Evaluated quote: totalPrice={}, maxBudget={}, accepted={}", totalPrice, maxBudget, accepted);
    }
}
