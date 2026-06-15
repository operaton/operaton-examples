package org.operaton.examples.incidentmanagement;

import org.operaton.bpm.engine.IdentityService;
import org.operaton.bpm.engine.identity.Group;
import org.operaton.bpm.engine.identity.User;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements ApplicationRunner {

    private final IdentityService identityService;

    public DataInitializer(IdentityService identityService) {
        this.identityService = identityService;
    }

    @Override
    public void run(ApplicationArguments args) {
        createGroupIfAbsent("employees", "Employees", "WORKFLOW");
        createGroupIfAbsent("firstline", "First-Line Support", "WORKFLOW");
        createGroupIfAbsent("secondline", "Second-Line Engineering", "WORKFLOW");

        createUserIfAbsent("alice", "Alice", "Employee", "alice@example.com", "alice");
        createUserIfAbsent("frank", "Frank", "FirstLine", "frank@example.com", "frank");
        createUserIfAbsent("grace", "Grace", "SecondLine", "grace@example.com", "grace");

        createMembershipIfAbsent("alice", "employees");
        createMembershipIfAbsent("frank", "firstline");
        createMembershipIfAbsent("grace", "secondline");
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
