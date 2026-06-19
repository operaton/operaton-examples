package org.operaton.examples.xsltscripttask;

import org.junit.jupiter.api.Test;
import org.operaton.bpm.engine.HistoryService;
import org.operaton.bpm.engine.RuntimeService;
import org.operaton.bpm.engine.history.HistoricProcessInstance;
import org.operaton.bpm.engine.history.HistoricVariableInstance;
import org.operaton.bpm.engine.runtime.ProcessInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class XsltScriptTaskIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired RuntimeService runtimeService;
    @Autowired HistoryService historyService;

    @Test
    void validOrderXmlIsTransformedToInvoice() throws IOException {
        String inputXml = loadResource("org/operaton/examples/xsltscripttask/sample-input.xml");

        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
            "xslt-script-task", Map.of("inputXml", inputXml));

        HistoricProcessInstance historic = historicInstance(instance);
        assertThat(historic.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
        assertThat(historic.getEndActivityId()).isEqualTo("EndEvent_Transformed");

        String result = (String) historicVariable(instance, "transformedXml");
        assertThat(result).contains("<invoiceNumber>INV-ORD-001</invoiceNumber>");
        assertThat(result).contains("<billTo>Alice Smith</billTo>");
        assertThat(result).contains("<amount>250.00</amount>");
    }

    @Test
    void invalidXmlTriggersErrorPath() {
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
            "xslt-script-task", Map.of("inputXml", "this is not xml <<< invalid"));

        HistoricProcessInstance historic = historicInstance(instance);
        assertThat(historic.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
        assertThat(historic.getEndActivityId()).isEqualTo("EndEvent_TransformFailed");
    }

    private HistoricProcessInstance historicInstance(ProcessInstance instance) {
        return historyService.createHistoricProcessInstanceQuery()
            .processInstanceId(instance.getId())
            .singleResult();
    }

    private Object historicVariable(ProcessInstance instance, String name) {
        HistoricVariableInstance var = historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(instance.getId())
            .variableName(name)
            .singleResult();
        return var == null ? null : var.getValue();
    }

    private String loadResource(String path) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(is).as("resource not found: " + path).isNotNull();
            return new String(is.readAllBytes());
        }
    }
}
