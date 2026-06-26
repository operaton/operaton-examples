package org.operaton.examples.contractsigning;

import org.operaton.bpm.engine.RuntimeService;
import org.operaton.bpm.engine.variable.Variables;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/contracts")
public class ContractController {
    private final DocumentStore documentStore;
    private final RuntimeService runtimeService;

    public ContractController(DocumentStore documentStore, RuntimeService runtimeService) {
        this.documentStore = documentStore;
        this.runtimeService = runtimeService;
    }

    @PostMapping
    public ResponseEntity<ContractUploadResponse> uploadContract(
            @RequestParam MultipartFile file,
            @RequestParam String customer,
            @RequestParam String company) {
        if (file.isEmpty() || !file.getContentType().equals("application/pdf")) {
            return ResponseEntity.badRequest().build();
        }
        if (file.getSize() > 20 * 1024 * 1024) return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();

        try {
            String draftKey = "contracts/" + UUID.randomUUID() + "/draft.pdf";
            documentStore.put(draftKey, file.getInputStream(), file.getSize());
            String businessKey = "CONTRACT-" + UUID.randomUUID();
            var pi = runtimeService.startProcessInstanceByKey("contract-signing", businessKey,
                Variables.createVariables()
                    .putValue("draftKey", draftKey)
                    .putValue("fileName", file.getOriginalFilename())
                    .putValue("customer", customer)
                    .putValue("company", company));
            return ResponseEntity.status(HttpStatus.CREATED).body(new ContractUploadResponse(pi.getId(), businessKey, draftKey));
        } catch (IOException e) { return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build(); }
    }

    public static class ContractUploadResponse {
        public String processInstanceId;
        public String businessKey;
        public String draftKey;

        public ContractUploadResponse(String pi, String bk, String dk) {
            this.processInstanceId = pi;
            this.businessKey = bk;
            this.draftKey = dk;
        }

        public String getProcessInstanceId() { return processInstanceId; }
        public void setProcessInstanceId(String id) { this.processInstanceId = id; }

        public String getBusinessKey() { return businessKey; }
        public void setBusinessKey(String key) { this.businessKey = key; }

        public String getDraftKey() { return draftKey; }
        public void setDraftKey(String key) { this.draftKey = key; }
    }
}
