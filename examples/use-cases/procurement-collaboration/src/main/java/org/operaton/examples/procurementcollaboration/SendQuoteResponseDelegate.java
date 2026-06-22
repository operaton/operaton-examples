package org.operaton.examples.procurementcollaboration;

import org.operaton.bpm.engine.RuntimeService;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("sendQuoteResponseDelegate")
public class SendQuoteResponseDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(SendQuoteResponseDelegate.class);

    private final RuntimeService runtimeService;

    SendQuoteResponseDelegate(RuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }

    @Override
    public void execute(DelegateExecution execution) {
        String requestId = (String) execution.getVariable("requestId");
        Integer unitPrice = (Integer) execution.getVariable("unitPrice");
        Integer totalPrice = (Integer) execution.getVariable("totalPrice");
        runtimeService.createMessageCorrelation("QuoteResponse")
            .processInstanceVariableEquals("requestId", requestId)
            .setVariable("unitPrice", unitPrice)
            .setVariable("totalPrice", totalPrice)
            .correlate();
        log.info("Sent QuoteResponse: requestId={}, unitPrice={}, totalPrice={}", requestId, unitPrice, totalPrice);
    }
}
