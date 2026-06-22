package org.operaton.examples.travelbooking;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("cancelCarDelegate")
public class CancelCarDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(CancelCarDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        String carRef = (String) execution.getVariable("carRef");
        execution.setVariable("carCancelled", true);
        log.info("Car cancelled: {}", carRef);
    }
}
