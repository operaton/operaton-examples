package org.operaton.examples.expensereimbursement;

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
        createGroupIfAbsent("finance", "Finance");
        createUserIfAbsent("alice", "Alice", "Müller", "alice");
        createUserIfAbsent("bob", "Bob", "Schmidt", "bob");
        addToGroupIfAbsent("alice", "finance");
        addToGroupIfAbsent("bob", "finance");
    }

    private void createGroupIfAbsent(String id, String name) {
        if (identityService.createGroupQuery().groupId(id).count() == 0) {
            Group g = identityService.newGroup(id);
            g.setName(name);
            identityService.saveGroup(g);
        }
    }

    private void createUserIfAbsent(String id, String firstName, String lastName, String password) {
        if (identityService.createUserQuery().userId(id).count() == 0) {
            User u = identityService.newUser(id);
            u.setFirstName(firstName);
            u.setLastName(lastName);
            u.setPassword(password);
            identityService.saveUser(u);
        }
    }

    private void addToGroupIfAbsent(String userId, String groupId) {
        boolean already = identityService.createUserQuery()
            .userId(userId).memberOfGroup(groupId).count() > 0;
        if (!already) {
            identityService.createMembership(userId, groupId);
        }
    }
}
