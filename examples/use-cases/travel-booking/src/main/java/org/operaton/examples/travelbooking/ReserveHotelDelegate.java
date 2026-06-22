package org.operaton.examples.travelbooking;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("reserveHotelDelegate")
public class ReserveHotelDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(ReserveHotelDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        String tripId = (String) execution.getVariable("tripId");
        String hotelRef = "HT-" + tripId;
        execution.setVariable("hotelRef", hotelRef);
        log.info("Hotel reserved: {} for trip {}", hotelRef, tripId);
    }
}
