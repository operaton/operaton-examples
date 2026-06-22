package org.operaton.examples.procurementcollaboration;

import org.operaton.bpm.engine.RuntimeService;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("sendQuoteRequestDelegate")
public class SendQuoteRequestDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(SendQuoteRequestDelegate.class);

    @Autowired
    private RuntimeService runtimeService;

    @Override
    public void execute(DelegateExecution execution) {
        String requestId = (String) execution.getVariable("requestId");
        String item = (String) execution.getVariable("item");
        Integer quantity = (Integer) execution.getVariable("quantity");
        runtimeService.createMessageCorrelation("QuoteRequest")
            .setVariable("requestId", requestId)
            .setVariable("item", item)
            .setVariable("quantity", quantity)
            .correlateStartMessage();
        log.info("Sent QuoteRequest: requestId={}, item={}, quantity={}", requestId, item, quantity);
    }
}
