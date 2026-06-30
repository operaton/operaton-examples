package org.operaton.examples.bankaccountopening;

import org.operaton.bpm.engine.RuntimeService;
import org.operaton.bpm.engine.runtime.ProcessInstance;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final RuntimeService runtimeService;

    public AccountController(RuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> startApplication(@RequestBody Map<String, Object> variables) {
        String applicationId = UUID.randomUUID().toString();
        variables.put("applicationId", applicationId);
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
            "bank-account-opening", applicationId, variables);
        return ResponseEntity.ok(Map.of(
            "applicationId", applicationId,
            "processInstanceId", instance.getId()));
    }
}
