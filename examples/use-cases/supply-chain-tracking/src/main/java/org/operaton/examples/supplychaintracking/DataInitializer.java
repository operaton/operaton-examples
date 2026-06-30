package org.operaton.examples.supplychaintracking;

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
        createGroupIfAbsent("logistics", "Logistics Team", "WORKFLOW");
        createGroupIfAbsent("customerService", "Customer Service", "WORKFLOW");

        if (identityService.createUserQuery().userId("alice").count() == 0) {
            createUser("alice", "Alice", "Logistics", "alice@example.com", "alice");
            identityService.createMembership("alice", "logistics");
        }
        if (identityService.createUserQuery().userId("bob").count() == 0) {
            createUser("bob", "Bob", "Service", "bob@example.com", "bob");
            identityService.createMembership("bob", "customerService");
        }
    }

    private void createGroupIfAbsent(String id, String name, String type) {
        if (identityService.createGroupQuery().groupId(id).count() == 0) {
            Group group = identityService.newGroup(id);
            group.setName(name);
            group.setType(type);
            identityService.saveGroup(group);
        }
    }

    private void createUser(String id, String first, String last, String email, String password) {
        User user = identityService.newUser(id);
        user.setFirstName(first);
        user.setLastName(last);
        user.setEmail(email);
        user.setPassword(password);
        identityService.saveUser(user);
    }
}
