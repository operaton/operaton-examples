package org.operaton.examples.complaintresolution;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("assessComplaintDelegate")
public class AssessComplaintDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(AssessComplaintDelegate.class);
    private static final double AUTHORITY_LIMIT = 500.0;

    @Override
    public void execute(DelegateExecution execution) {
        Double requestedRefund = (Double) execution.getVariable("requestedRefund");
        boolean withinAuthority = requestedRefund != null && requestedRefund <= AUTHORITY_LIMIT;
        execution.setVariable("refundWithinAuthority", withinAuthority);
        log.info("Complaint assessed: requestedRefund={}, withinAuthority={}", requestedRefund, withinAuthority);
    }
}
