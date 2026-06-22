package org.operaton.examples.travelbooking;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("cancelHotelDelegate")
public class CancelHotelDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(CancelHotelDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        String hotelRef = (String) execution.getVariable("hotelRef");
        execution.setVariable("hotelCancelled", true);
        log.info("Hotel cancelled: {}", hotelRef);
    }
}
