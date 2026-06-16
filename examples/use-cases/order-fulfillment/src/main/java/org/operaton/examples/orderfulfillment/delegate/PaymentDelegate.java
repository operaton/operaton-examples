package org.operaton.examples.orderfulfillment.delegate;

import org.operaton.bpm.engine.delegate.BpmnError;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component("paymentDelegate")
public class PaymentDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(PaymentDelegate.class);

    @Value("${payment.service.url:http://localhost:8089}")
    private String paymentServiceUrl;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        Boolean simulateFailure = (Boolean) execution.getVariable("simulatePaymentFailure");
        if (Boolean.TRUE.equals(simulateFailure)) {
            throw new BpmnError("PAYMENT_FAILED", "Payment failed due to simulation flag");
        }
        String url = paymentServiceUrl + "/payments";
        String response = new RestTemplate().postForObject(url, null, String.class);
        log.info("Payment processed for order {}: {}", execution.getVariable("orderId"), response);
    }
}
