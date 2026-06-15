package org.operaton.examples.distributionwildfly;

import org.operaton.bpm.application.ProcessApplication;
import org.operaton.bpm.application.impl.JakartaServletProcessApplication;

// JakartaServletProcessApplicationDeployer (SCI in the engine module) registers this automatically;
// @WebListener is not needed and @ProcessApplication name is used as the deployment name.
@ProcessApplication("document-approval-app")
public class DocumentApprovalApplication extends JakartaServletProcessApplication {
}
