package org.operaton.examples.leaverequest.delegate;

import org.operaton.examples.leaverequest.VacationBalanceService;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component
public class FinalizeLeaveApprovalDelegate implements JavaDelegate {

    private final VacationBalanceService vacationBalanceService;

    public FinalizeLeaveApprovalDelegate(VacationBalanceService vacationBalanceService) {
        this.vacationBalanceService = vacationBalanceService;
    }

    @Override
    public void execute(DelegateExecution execution) {
        String requester = (String) execution.getVariable("requester");
        if (requester == null || requester.isBlank()) {
            throw new IllegalStateException("Leave requester must be present before finalizing approved leave.");
        }
        int durationDays = readDurationDays(execution.getVariable("durationDays"));

        vacationBalanceService.deductVacationDays(requester, durationDays);
        execution.setVariable("leaveStatus", "APPROVED");
    }

    private int readDurationDays(Object durationValue) {
        int durationDays = durationValue instanceof Number number
                ? number.intValue()
                : Integer.parseInt(String.valueOf(durationValue));
        if (durationDays < 1) {
            throw new IllegalArgumentException("durationDays must be at least 1 before finalizing approved leave.");
        }
        return durationDays;
    }
}
