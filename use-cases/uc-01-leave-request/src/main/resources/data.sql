-- Initial vacation balances for seeded employees
INSERT INTO employee_vacation_balance (employee_id, remaining_vacation_days)
VALUES ('alice', 30), ('bob', 30), ('carol', 30)
ON CONFLICT (employee_id) DO NOTHING;
