package org.operaton.examples.candidatescreening;

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
class CandidateScreeningDeploymentIT {

    @Container
    @ServiceConnection
    @SuppressWarnings("rawtypes")
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired RepositoryService repositoryService;
    @Autowired IdentityService identityService;

    @Test
    void processIsDeployed() {
        long count = repositoryService.createProcessDefinitionQuery()
            .processDefinitionKey("candidate-screening").count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void recruiterGroupAndUserSeeded() {
        assertThat(identityService.createGroupQuery().groupId("recruiters").count()).isEqualTo(1);
        assertThat(identityService.createUserQuery().userId("rachel").count()).isEqualTo(1);
    }
}
