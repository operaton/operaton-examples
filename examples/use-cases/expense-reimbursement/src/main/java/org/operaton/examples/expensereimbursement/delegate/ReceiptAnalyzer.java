package org.operaton.examples.expensereimbursement.delegate;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.operaton.bpm.engine.variable.value.FileValue;
import org.operaton.examples.expensereimbursement.LlmClient;
import org.operaton.examples.expensereimbursement.PromptBuilder;
import org.operaton.examples.expensereimbursement.ResponseParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Base64;

@Component("receiptAnalyzer")
public class ReceiptAnalyzer implements JavaDelegate {
    private static final Logger log = LoggerFactory.getLogger(ReceiptAnalyzer.class);

    private final LlmClient llmClient;
    private final PromptBuilder promptBuilder;
    private final ResponseParser responseParser;

    public ReceiptAnalyzer(LlmClient llmClient, PromptBuilder promptBuilder, ResponseParser responseParser) {
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        this.responseParser = responseParser;
    }

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String requesterName = (String) execution.getVariable("requesterName");
        double statedCost = ((Number) execution.getVariable("statedCost")).doubleValue();
        String kind = (String) execution.getVariable("kind");

        try {
            FileValue receipt = execution.getVariableTyped("receipt");
            byte[] bytes = receipt.getValue().readAllBytes();
            String base64 = Base64.getEncoder().encodeToString(bytes);

            String request = promptBuilder.receiptAnalysisRequest(base64, requesterName, statedCost, kind);
            String response = llmClient.call(request);

            execution.setVariable("matchResult", responseParser.matchResult(response));
            execution.setVariable("extractedName", responseParser.extractedName(response));
            execution.setVariable("extractedCost", responseParser.extractedCost(response));
            execution.setVariable("analysisNotes", responseParser.analysisNotes(response));
        } catch (Exception e) {
            log.warn("Receipt analysis failed — defaulting to UNRELATED: {}", e.getMessage());
            execution.setVariable("matchResult", "UNRELATED");
            execution.setVariable("extractedName", "");
            execution.setVariable("extractedCost", 0.0);
            execution.setVariable("analysisNotes", "Analysis failed: " + e.getMessage());
        }
    }
}
