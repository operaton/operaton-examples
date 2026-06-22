package org.operaton.examples.insuranceclaim;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("requestDocumentsDelegate")
public class RequestDocumentsDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(RequestDocumentsDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        String claimNumber = (String) execution.getVariable("claimNumber");
        String policyNumber = (String) execution.getVariable("policyNumber");
        log.info("Requesting documents for claim {} from policy {}", claimNumber, policyNumber);
    }
}
