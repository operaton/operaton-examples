package org.operaton.examples.leaverequest;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class VacationBalanceService {

    private final JdbcTemplate jdbcTemplate;

    public VacationBalanceService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void seedBalanceIfAbsent(String employeeId, int remainingVacationDays) {
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM employee_vacation_balance WHERE employee_id = ?",
                Integer.class,
                employeeId
        );
        if (existing != null && existing == 0) {
            jdbcTemplate.update(
                    "INSERT INTO employee_vacation_balance (employee_id, remaining_vacation_days) VALUES (?, ?)",
                    employeeId,
                    remainingVacationDays
            );
        }
    }

    public int getRemainingVacationDays(String employeeId) {
        Integer remaining = findRemainingVacationDays(employeeId);
        if (remaining == null) {
            throw new IllegalStateException("No vacation balance found for employee '" + employeeId + "'.");
        }
        return remaining;
    }

    public Integer findRemainingVacationDays(String employeeId) {
        List<Integer> balances = jdbcTemplate.query(
                "SELECT remaining_vacation_days FROM employee_vacation_balance WHERE employee_id = ?",
                (rs, rowNum) -> rs.getInt("remaining_vacation_days"),
                employeeId
        );
        return balances.isEmpty() ? null : balances.get(0);
    }

    @Transactional
    public int deductVacationDays(String employeeId, int requestedDays) {
        if (requestedDays < 1) {
            throw new IllegalArgumentException("Requested vacation days must be at least 1.");
        }
        int updated = jdbcTemplate.update(
                """
                UPDATE employee_vacation_balance
                   SET remaining_vacation_days = remaining_vacation_days - ?
                 WHERE employee_id = ?
                   AND remaining_vacation_days >= ?
                """,
                requestedDays,
                employeeId,
                requestedDays
        );
        if (updated != 1) {
            throw new IllegalStateException(
                    "Unable to deduct " + requestedDays + " vacation days for employee '" + employeeId + "'."
            );
        }
        return getRemainingVacationDays(employeeId);
    }
}
