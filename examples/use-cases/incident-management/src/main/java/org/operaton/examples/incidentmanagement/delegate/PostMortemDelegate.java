package org.operaton.examples.incidentmanagement.delegate;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class PostMortemDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(PostMortemDelegate.class);

    @Value("${notification.service.url:http://localhost:8089}")
    private String notificationServiceUrl;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String url = notificationServiceUrl + "/notifications/post-mortem";
        new RestTemplate().postForObject(url, null, String.class);
        log.info("Post-mortem notification sent for incident {}", execution.getVariable("incidentId"));
    }
}
