package org.operaton.examples.loanapplication.delegate;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class CreditScoreDelegate implements JavaDelegate {

    @Value("${credit.score.service.url:http://localhost:8089}/credit-score")
    private String creditScoreUrl;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        Object existingScore = execution.getVariable("creditScore");
        if (existingScore instanceof Number score) {
            execution.setVariable("creditScore", score.intValue());
            execution.setVariable("loanDecision", "PENDING");
            ensureLoanAmount(execution);
            return;
        }

        RestTemplate restTemplate = new RestTemplate();
        CreditScoreResponse response = restTemplate.getForObject(creditScoreUrl, CreditScoreResponse.class);
        if (response == null) {
            throw new IllegalStateException("Credit score service returned no response");
        }

        int score = response.score();
        execution.setVariable("creditScore", score);
        execution.setVariable("loanDecision", "PENDING");

        ensureLoanAmount(execution);
    }

    private void ensureLoanAmount(DelegateExecution execution) {
        Object loanAmount = execution.getVariable("loanAmount");
        if (loanAmount == null) {
            execution.setVariable("loanAmount", 100000);
        }
    }

    record CreditScoreResponse(int score) {}
}
