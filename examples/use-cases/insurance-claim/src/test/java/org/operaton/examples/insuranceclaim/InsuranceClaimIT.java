package org.operaton.examples.insuranceclaim;

import org.junit.jupiter.api.Test;
import org.operaton.bpm.engine.ProcessEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class InsuranceClaimIT {

    @Container
    @ServiceConnection
    @SuppressWarnings("rawtypes")
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired ProcessEngine processEngine;

    @Test
    void processDefinitionIsDeployed() {
        assertThat(processEngine.getRepositoryService()
            .createProcessDefinitionQuery()
            .processDefinitionKey("insurance-claim")
            .count()).isEqualTo(1);
    }

    @Test
    void settlementDecisionMapsCorrectly() {
        var ds = processEngine.getDecisionService();

        // fraudSuspected → always reject regardless of other inputs
        var result = ds.evaluateDecisionByKey("claim-settlement")
            .variables(org.operaton.bpm.engine.variable.Variables.createVariables()
                .putValue("fraudSuspected", true)
                .putValue("claimType", "collision")
                .putValue("appraisedAmount", 500.0))
            .evaluate().getSingleResult();
        assertThat((String) result.getEntry("settlementDecision")).isEqualTo("reject");
        assertThat((Double) result.getEntry("approvedAmount")).isEqualTo(0.0);

        // flood → reject regardless of amount or fraud
        result = ds.evaluateDecisionByKey("claim-settlement")
            .variables(org.operaton.bpm.engine.variable.Variables.createVariables()
                .putValue("fraudSuspected", false)
                .putValue("claimType", "flood")
                .putValue("appraisedAmount", 5000.0))
            .evaluate().getSingleResult();
        assertThat((String) result.getEntry("settlementDecision")).isEqualTo("reject");
        assertThat((Double) result.getEntry("approvedAmount")).isEqualTo(0.0);

        // small collision (<=1000) → approve, full appraised amount
        result = ds.evaluateDecisionByKey("claim-settlement")
            .variables(org.operaton.bpm.engine.variable.Variables.createVariables()
                .putValue("fraudSuspected", false)
                .putValue("claimType", "collision")
                .putValue("appraisedAmount", 720.0))
            .evaluate().getSingleResult();
        assertThat((String) result.getEntry("settlementDecision")).isEqualTo("approve");
        assertThat((Double) result.getEntry("approvedAmount")).isEqualTo(720.0);

        // medium collision (<=50000) → approve at 80%
        result = ds.evaluateDecisionByKey("claim-settlement")
            .variables(org.operaton.bpm.engine.variable.Variables.createVariables()
                .putValue("fraudSuspected", false)
                .putValue("claimType", "collision")
                .putValue("appraisedAmount", 5000.0))
            .evaluate().getSingleResult();
        assertThat((String) result.getEntry("settlementDecision")).isEqualTo("approve");
        assertThat((Double) result.getEntry("approvedAmount")).isEqualTo(4000.0);

        // large amount (>50000) → reject
        result = ds.evaluateDecisionByKey("claim-settlement")
            .variables(org.operaton.bpm.engine.variable.Variables.createVariables()
                .putValue("fraudSuspected", false)
                .putValue("claimType", "collision")
                .putValue("appraisedAmount", 60000.0))
            .evaluate().getSingleResult();
        assertThat((String) result.getEntry("settlementDecision")).isEqualTo("reject");
    }
}
