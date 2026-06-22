package org.operaton.examples.travelbooking;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("reserveCarDelegate")
public class ReserveCarDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(ReserveCarDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        String tripId = (String) execution.getVariable("tripId");
        String carRef = "CR-" + tripId;
        execution.setVariable("carRef", carRef);
        log.info("Car reserved: {} for trip {}", carRef, tripId);
    }
}
