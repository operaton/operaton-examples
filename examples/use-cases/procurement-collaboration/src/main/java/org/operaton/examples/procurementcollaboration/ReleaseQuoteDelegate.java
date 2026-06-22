package org.operaton.examples.procurementcollaboration;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("releaseQuoteDelegate")
public class ReleaseQuoteDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(ReleaseQuoteDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        log.info("Quote released for requestId={}", execution.getVariable("requestId"));
    }
}
