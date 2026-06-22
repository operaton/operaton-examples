package org.operaton.examples.complaintresolution;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("closeComplaintDelegate")
public class CloseComplaintDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(CloseComplaintDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        String complaintId = (String) execution.getVariable("complaintId");
        log.info("Complaint closed: complaintId={}", complaintId);
    }
}
