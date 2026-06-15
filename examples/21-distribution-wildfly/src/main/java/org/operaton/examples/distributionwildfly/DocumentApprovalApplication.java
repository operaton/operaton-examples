package org.operaton.examples.distributionwildfly;

import jakarta.servlet.annotation.WebListener;
import org.operaton.bpm.application.ProcessApplication;
import org.operaton.bpm.application.impl.ServletProcessApplication;

@WebListener
@ProcessApplication("document-approval-app")
public class DocumentApprovalApplication extends ServletProcessApplication {
}
