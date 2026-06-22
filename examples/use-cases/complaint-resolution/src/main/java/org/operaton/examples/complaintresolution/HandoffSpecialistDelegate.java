package org.operaton.examples.complaintresolution;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("handoffSpecialistDelegate")
public class HandoffSpecialistDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(HandoffSpecialistDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        String complaintId = (String) execution.getVariable("complaintId");
        execution.setVariable("specialistHandoff", true);
        log.info("Complaint escalated to specialist: complaintId={}", complaintId);
    }
}
