package org.operaton.examples.expensereimbursement;

import org.junit.jupiter.api.Test;
import org.operaton.bpm.engine.IdentityService;
import org.operaton.bpm.engine.RepositoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class ExpenseReimbursementDeploymentIT {

    @Container
    @ServiceConnection
    @SuppressWarnings("rawtypes")
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired RepositoryService repositoryService;
    @Autowired IdentityService identityService;

    @Test
    void processDeploysSuccessfully() {
        long count = repositoryService.createProcessDefinitionQuery()
            .processDefinitionKey("expense-reimbursement").count();
        assertThat(count).isGreaterThan(0);
    }

    @Test
    void decisionDeploysSuccessfully() {
        long count = repositoryService.createDecisionDefinitionQuery()
            .decisionDefinitionKey("reimbursement-approval").count();
        assertThat(count).isGreaterThan(0);
    }

    @Test
    void financeGroupIsSeeded() {
        long count = identityService.createGroupQuery().groupId("finance").count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void financeUsersAreSeeded() {
        long alice = identityService.createUserQuery().userId("alice").memberOfGroup("finance").count();
        long bob   = identityService.createUserQuery().userId("bob").memberOfGroup("finance").count();
        assertThat(alice).isEqualTo(1);
        assertThat(bob).isEqualTo(1);
    }
}
