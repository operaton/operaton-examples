package org.operaton.examples.errorcompensation.delegate;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("bookFlightDelegate")
public class BookFlightDelegate implements JavaDelegate {
    @Override
    public void execute(DelegateExecution execution) {
        execution.setVariable("flightBooked", true);
        execution.setVariable("flightBookingRef", "FLIGHT-" + execution.getVariable("tripId"));
    }
}
