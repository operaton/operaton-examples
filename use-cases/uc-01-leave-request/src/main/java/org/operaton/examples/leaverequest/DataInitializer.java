package org.operaton.examples.leaverequest;

import org.operaton.bpm.engine.IdentityService;
import org.operaton.bpm.engine.identity.Group;
import org.operaton.bpm.engine.identity.User;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements ApplicationRunner {

    private final IdentityService identityService;
    private final VacationBalanceService vacationBalanceService;

    public DataInitializer(IdentityService identityService, VacationBalanceService vacationBalanceService) {
        this.identityService = identityService;
        this.vacationBalanceService = vacationBalanceService;
    }

    @Override
    public void run(ApplicationArguments args) {
        createGroupIfAbsent("employees", "Employees", "WORKFLOW");
        createGroupIfAbsent("managers", "Managers", "WORKFLOW");
        createGroupIfAbsent("hr", "HR Department", "WORKFLOW");
        createGroupIfAbsent("operaton-admin", "Operaton Administrators", "SYSTEM");

        createUserIfAbsent("alice", "Alice", "Employee", "alice@example.com", "alice");
        createUserIfAbsent("bob", "Bob", "Manager", "bob@example.com", "bob");
        createUserIfAbsent("carol", "Carol", "HR", "carol@example.com", "carol");
        createUserIfAbsent("admin", "Admin", "User", "admin@example.com", "admin");

        createMembershipIfAbsent("alice", "employees");
        createMembershipIfAbsent("bob", "managers");
        createMembershipIfAbsent("carol", "hr");
        createMembershipIfAbsent("admin", "operaton-admin");

        vacationBalanceService.seedBalanceIfAbsent("alice", 30);
    }

    private void createGroupIfAbsent(String id, String name, String type) {
        if (identityService.createGroupQuery().groupId(id).count() == 0) {
            Group group = identityService.newGroup(id);
            group.setName(name);
            group.setType(type);
            identityService.saveGroup(group);
        }
    }

    private void createUserIfAbsent(String id, String first, String last, String email, String password) {
        if (identityService.createUserQuery().userId(id).count() == 0) {
            User user = identityService.newUser(id);
            user.setFirstName(first);
            user.setLastName(last);
            user.setEmail(email);
            user.setPassword(password);
            identityService.saveUser(user);
        }
    }

    private void createMembershipIfAbsent(String userId, String groupId) {
        if (identityService.createUserQuery().userId(userId).memberOfGroup(groupId).count() == 0) {
            identityService.createMembership(userId, groupId);
        }
    }
}
