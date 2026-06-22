package org.operaton.examples.insuranceclaim;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("closeIncompleteClaimDelegate")
public class CloseIncompleteClaimDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(CloseIncompleteClaimDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        String claimNumber = (String) execution.getVariable("claimNumber");
        log.info("Closing claim {} — no documents received within deadline", claimNumber);
    }
}
