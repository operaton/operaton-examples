package org.operaton.examples.travelbooking;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("sendConfirmationDelegate")
public class SendConfirmationDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(SendConfirmationDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        String tripId    = (String) execution.getVariable("tripId");
        String flightRef = (String) execution.getVariable("flightRef");
        String hotelRef  = (String) execution.getVariable("hotelRef");
        String carRef    = (String) execution.getVariable("carRef");
        log.info("Trip {} confirmed — flight={}, hotel={}, car={}", tripId, flightRef, hotelRef, carRef);
    }
}
