package org.operaton.examples.complaintresolution;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("approveRefundDelegate")
public class ApproveRefundDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(ApproveRefundDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        Double requestedRefund = (Double) execution.getVariable("requestedRefund");
        execution.setVariable("refundApproved", true);
        log.info("Refund approved by manager: amount={}", requestedRefund);
    }
}
