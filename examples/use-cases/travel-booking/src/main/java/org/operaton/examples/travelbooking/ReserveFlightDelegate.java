package org.operaton.examples.travelbooking;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("reserveFlightDelegate")
public class ReserveFlightDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(ReserveFlightDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        String tripId = (String) execution.getVariable("tripId");
        String flightRef = "FL-" + tripId;
        execution.setVariable("flightRef", flightRef);
        log.info("Flight reserved: {} for trip {}", flightRef, tripId);
    }
}
