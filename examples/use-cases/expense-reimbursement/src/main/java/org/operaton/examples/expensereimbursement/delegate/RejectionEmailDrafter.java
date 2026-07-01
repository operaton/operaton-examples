package org.operaton.examples.expensereimbursement.delegate;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.operaton.examples.expensereimbursement.LlmClient;
import org.operaton.examples.expensereimbursement.PromptBuilder;
import org.operaton.examples.expensereimbursement.ResponseParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("rejectionEmailDrafter")
public class RejectionEmailDrafter implements JavaDelegate {
    private static final Logger log = LoggerFactory.getLogger(RejectionEmailDrafter.class);

    private final LlmClient llmClient;
    private final PromptBuilder promptBuilder;
    private final ResponseParser responseParser;

    public RejectionEmailDrafter(LlmClient llmClient, PromptBuilder promptBuilder, ResponseParser responseParser) {
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        this.responseParser = responseParser;
    }

    @Override
    public void execute(DelegateExecution execution) {
        String requesterName = (String) execution.getVariable("requesterName");
        double statedCost = ((Number) execution.getVariable("statedCost")).doubleValue();
        String kind = (String) execution.getVariable("kind");
        String reason = (String) execution.getVariable("reason");

        try {
            String request = promptBuilder.rejectionEmailRequest(requesterName, statedCost, kind, reason);
            String response = llmClient.call(request);
            execution.setVariable("emailBody", responseParser.emailBody(response));
        } catch (Exception e) {
            log.warn("Rejection email drafting failed — using fallback: {}", e.getMessage());
            execution.setVariable("emailBody",
                "Dear " + requesterName + ",\n\nWe regret to inform you that your expense reimbursement request of " +
                statedCost + " EUR has not been approved.\n\nKind regards");
        }
        execution.setVariable("emailSubject", "Expense Reimbursement Update");
    }
}
