package org.operaton.examples.eventsubprocess.delegate;

import org.operaton.bpm.engine.delegate.BpmnError;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("enrichDocumentDelegate")
public class EnrichDocumentDelegate implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        Boolean shouldFail = (Boolean) execution.getVariable("simulateError");
        if (Boolean.TRUE.equals(shouldFail)) {
            throw new BpmnError("DOCUMENT_ERROR", "Document enrichment failed");
        }
        execution.setVariable("enriched", true);
    }
}
