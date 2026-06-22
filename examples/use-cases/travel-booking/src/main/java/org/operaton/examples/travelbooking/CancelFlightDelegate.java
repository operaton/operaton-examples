package org.operaton.examples.travelbooking;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("cancelFlightDelegate")
public class CancelFlightDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(CancelFlightDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        String flightRef = (String) execution.getVariable("flightRef");
        execution.setVariable("flightCancelled", true);
        log.info("Flight cancelled: {}", flightRef);
    }
}
