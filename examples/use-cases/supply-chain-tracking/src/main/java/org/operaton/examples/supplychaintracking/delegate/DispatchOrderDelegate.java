package org.operaton.examples.supplychaintracking.delegate;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;

@Component("dispatchOrderDelegate")
public class DispatchOrderDelegate implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        execution.setVariable("status", "IN_TRANSIT");
        execution.setVariable("dispatchedAt", Instant.now().toString());
        execution.setVariable("eta", LocalDate.now().plusDays(5).toString());
    }
}
