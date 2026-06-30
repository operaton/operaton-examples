package org.operaton.examples.bankaccountopening.delegate;

import org.operaton.bpm.client.task.ExternalTask;
import org.operaton.bpm.client.task.ExternalTaskHandler;
import org.operaton.bpm.client.task.ExternalTaskService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;

@Component
public class IdentityValidationWorker implements ExternalTaskHandler {

    @Override
    public void execute(ExternalTask task, ExternalTaskService service) {
        String docNumber = task.getVariable("idDocumentNumber");
        String dobRaw = task.getVariable("dateOfBirth");

        boolean formatOk = docNumber != null && docNumber.matches("[A-Za-z]{2,}[0-9]{6,}");
        boolean dobOk = isReasonableDob(dobRaw);

        if (!formatOk || !dobOk) {
            service.complete(task, Map.of("identityVerified", false, "identityScore", 0));
            return;
        }

        // Deterministic score in [70, 99] based on hash of document number
        int hash = Math.abs(docNumber.hashCode());
        int score = 70 + (hash % 30);

        service.complete(task, Map.of("identityVerified", true, "identityScore", score));
    }

    private boolean isReasonableDob(String dob) {
        if (dob == null) return false;
        try {
            LocalDate date = LocalDate.parse(dob.substring(0, 10), DateTimeFormatter.ISO_LOCAL_DATE);
            LocalDate now = LocalDate.now();
            return !date.isAfter(now) && date.isAfter(now.minusYears(100));
        } catch (DateTimeParseException | StringIndexOutOfBoundsException e) {
            return false;
        }
    }
}
