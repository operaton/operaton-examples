package org.operaton.examples.leaverequest.delegate;

import org.operaton.examples.leaverequest.VacationBalanceService;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

@Component
public class LeaveRequestValidationDelegate implements JavaDelegate {

    private final VacationBalanceService vacationBalanceService;
    private final Clock leaveRequestClock;

    public LeaveRequestValidationDelegate(VacationBalanceService vacationBalanceService, Clock leaveRequestClock) {
        this.vacationBalanceService = vacationBalanceService;
        this.leaveRequestClock = leaveRequestClock;
    }

    @Override
    public void execute(DelegateExecution execution) {
        execution.setVariable("leaveStatus", "PENDING");
        String requester = readRequester(execution);
        LocalDate startDate = readStartDate(execution.getVariable("startDate"));
        int durationDays = readDurationDays(execution.getVariable("durationDays"));
        Integer remainingVacationDays = vacationBalanceService.findRemainingVacationDays(requester);
        execution.setVariable("requester", requester);
        execution.setVariable("startDate", startDate.toString());
        execution.setVariable("durationDays", durationDays);
        execution.setVariable("remainingVacationDays", remainingVacationDays == null ? 0 : remainingVacationDays.intValue());

        if (startDate.isAfter(LocalDate.MAX.minusDays(durationDays - 1L))) {
            reject(execution, "Requested range extends beyond the supported calendar range.");
            return;
        }

        LocalDate endDate = startDate.plusDays(durationDays - 1L);
        execution.setVariable("endDate", endDate.toString());

        if (remainingVacationDays == null) {
            reject(execution, "No vacation balance is configured for requester '" + requester + "'.");
            return;
        }

        execution.setVariable("validationPassed", true);
        execution.removeVariable("rejectionReason");

        if (startDate.isBefore(LocalDate.now(leaveRequestClock))) {
            reject(execution, "Start date must be today or later.");
            return;
        }

        if (durationDays > remainingVacationDays) {
            reject(
                    execution,
                    "Requested " + durationDays + " vacation days exceeds the remaining balance of "
                            + remainingVacationDays + " day(s)."
            );
        }
    }

    private String readRequester(DelegateExecution execution) {
        Object rawRequester = execution.getVariable("requester");
        if (rawRequester instanceof String requester && !requester.isBlank()) {
            return requester;
        }
        throw new IllegalStateException("Leave requester must be set from the authenticated process initiator.");
    }

    private LocalDate readStartDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof Date date) {
            return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return LocalDate.parse(stringValue);
        }
        throw new IllegalArgumentException("startDate must be provided as an ISO-8601 date.");
    }

    private int readDurationDays(Object value) {
        int durationDays;
        if (value instanceof Number number) {
            durationDays = number.intValue();
        } else if (value instanceof String stringValue && !stringValue.isBlank()) {
            durationDays = Integer.parseInt(stringValue);
        } else {
            throw new IllegalArgumentException("durationDays must be provided as a positive integer.");
        }

        if (durationDays < 1) {
            throw new IllegalArgumentException("durationDays must be at least 1.");
        }
        return durationDays;
    }

    private void reject(DelegateExecution execution, String rejectionReason) {
        execution.setVariable("validationPassed", false);
        execution.setVariable("rejectionReason", rejectionReason);
        execution.setVariable("leaveStatus", "REJECTED");
    }
}
