package org.operaton.examples.travelbooking;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("notifyCancellationDelegate")
public class NotifyCancellationDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(NotifyCancellationDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        String tripId = (String) execution.getVariable("tripId");
        log.info("Trip {} cancelled — all reservations have been released", tripId);
    }
}
