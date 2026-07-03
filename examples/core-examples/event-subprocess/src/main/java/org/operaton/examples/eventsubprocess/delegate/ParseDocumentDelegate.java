package org.operaton.examples.eventsubprocess.delegate;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("parseDocumentDelegate")
public class ParseDocumentDelegate implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        execution.setVariable("parsed", true);
        execution.setVariable("documentTitle", "Q1 Report");
    }
}
