package org.operaton.examples.eventsubprocess.delegate;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("handleDocumentErrorDelegate")
public class HandleDocumentErrorDelegate implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        execution.setVariable("errorHandled", true);
    }
}
