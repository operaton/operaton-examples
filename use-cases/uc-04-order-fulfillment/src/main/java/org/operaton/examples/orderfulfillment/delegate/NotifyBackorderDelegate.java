package org.operaton.examples.orderfulfillment.delegate;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component("notifyBackorderDelegate")
public class NotifyBackorderDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(NotifyBackorderDelegate.class);

    @Value("${notification.service.url:http://localhost:8089}")
    private String notificationServiceUrl;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String url = notificationServiceUrl + "/notifications/backorder";
        new RestTemplate().postForObject(url, null, String.class);
        log.info("Backorder notification sent for order {}", execution.getVariable("orderId"));
    }
}
