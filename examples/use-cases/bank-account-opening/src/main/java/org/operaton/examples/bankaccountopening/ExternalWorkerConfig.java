package org.operaton.examples.bankaccountopening;

import org.operaton.bpm.client.ExternalTaskClient;
import org.operaton.examples.bankaccountopening.delegate.IdentityValidationWorker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "operaton.worker.enabled", havingValue = "true", matchIfMissing = true)
public class ExternalWorkerConfig {

    @Value("${operaton.worker.base-url:http://localhost:8080/engine-rest}")
    private String baseUrl;

    public String getBaseUrl() {
        return baseUrl;
    }

    public boolean isEnabled() {
        // This bean is only created when the property is true (or missing), so always true here
        return true;
    }

    @Bean(destroyMethod = "stop")
    public ExternalTaskClient externalTaskClient(IdentityValidationWorker identityValidationWorker) {
        ExternalTaskClient client = ExternalTaskClient.create()
            .baseUrl(baseUrl)
            .lockDuration(10_000)
            .build();
        client.subscribe("identity-validation").handler(identityValidationWorker).open();
        return client;
    }
}
