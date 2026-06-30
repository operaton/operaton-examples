package org.operaton.examples.supplychaintracking.delegate;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("customerServiceFollowupDelegate")
public class CustomerServiceFollowupDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(CustomerServiceFollowupDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        String trackingNumber = (String) execution.getVariable("trackingNumber");
        execution.setVariable("status", "FOLLOW_UP_REQUIRED");
        log.warn("Shipment {} requires customer service follow-up after 7-day timeout", trackingNumber);
    }
}
