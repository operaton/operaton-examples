package org.operaton.examples.incidentmanagement.delegate;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class CloseTicketDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(CloseTicketDelegate.class);

    @Value("${ticket.service.url:http://localhost:8089}")
    private String ticketServiceUrl;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        Object incidentId = execution.getVariable("incidentId");
        String id = incidentId != null ? incidentId.toString() : "unknown";
        String url = ticketServiceUrl + "/tickets/" + id + "/close";
        new RestTemplate().postForObject(url, null, String.class);
        log.info("Ticket {} closed", id);
    }
}
