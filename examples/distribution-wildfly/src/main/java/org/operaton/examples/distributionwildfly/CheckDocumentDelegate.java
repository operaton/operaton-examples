package org.operaton.examples.distributionwildfly;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;

public class CheckDocumentDelegate implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String documentType = (String) execution.getVariable("documentType");
        boolean requiresReview = documentType != null
            && (documentType.equalsIgnoreCase("contract") || documentType.equalsIgnoreCase("legal"));
        execution.setVariable("requiresReview", requiresReview);
    }
}
