package org.operaton.examples.procurementcollaboration;

import org.operaton.bpm.engine.RuntimeService;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("sendOrderDecisionDelegate")
public class SendOrderDecisionDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(SendOrderDecisionDelegate.class);

    @Autowired
    private RuntimeService runtimeService;

    @Override
    public void execute(DelegateExecution execution) {
        String requestId = (String) execution.getVariable("requestId");
        Boolean accepted = (Boolean) execution.getVariable("accepted");
        runtimeService.createMessageCorrelation("OrderDecision")
            .processInstanceVariableEquals("requestId", requestId)
            .setVariable("accepted", accepted)
            .correlate();
        log.info("Sent OrderDecision: requestId={}, accepted={}", requestId, accepted);
    }
}
