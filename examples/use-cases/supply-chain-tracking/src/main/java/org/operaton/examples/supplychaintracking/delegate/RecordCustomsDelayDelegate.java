package org.operaton.examples.supplychaintracking.delegate;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component("recordCustomsDelayDelegate")
public class RecordCustomsDelayDelegate implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        Integer delayCount = (Integer) execution.getVariable("delayCount");
        int count = (delayCount == null) ? 0 : delayCount;
        execution.setVariable("delayCount", count + 1);

        String eta = (String) execution.getVariable("eta");
        LocalDate currentEta = (eta != null) ? LocalDate.parse(eta) : LocalDate.now();
        execution.setVariable("eta", currentEta.plusDays(7).toString());
        execution.setVariable("status", "CUSTOMS_HOLD");
    }
}
