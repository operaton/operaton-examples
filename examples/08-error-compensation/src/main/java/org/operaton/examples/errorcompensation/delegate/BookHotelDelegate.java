package org.operaton.examples.errorcompensation.delegate;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("bookHotelDelegate")
public class BookHotelDelegate implements JavaDelegate {
    @Override
    public void execute(DelegateExecution execution) {
        execution.setVariable("hotelBooked", true);
        execution.setVariable("hotelBookingRef", "HOTEL-" + execution.getVariable("tripId"));
    }
}
