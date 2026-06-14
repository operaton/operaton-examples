CREATE TABLE IF NOT EXISTS employee_vacation_balance (
    employee_id VARCHAR(64) PRIMARY KEY,
    remaining_vacation_days INTEGER NOT NULL CHECK (remaining_vacation_days >= 0)
);
