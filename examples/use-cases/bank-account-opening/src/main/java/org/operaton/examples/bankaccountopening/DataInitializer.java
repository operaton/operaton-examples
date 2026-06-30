package org.operaton.examples.bankaccountopening;

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
        createGroupIfAbsent("compliance", "Compliance");
        createUserIfAbsent("alice", "Alice", "Müller", "alice");
        createUserIfAbsent("bob", "Bob", "Schmidt", "bob");
        addToGroupIfAbsent("alice", "compliance");
        addToGroupIfAbsent("bob", "compliance");
    }

    private void createGroupIfAbsent(String id, String name) {
        if (identityService.createGroupQuery().groupId(id).count() == 0) {
            Group group = identityService.newGroup(id);
            group.setName(name);
            identityService.saveGroup(group);
        }
    }

    private void createUserIfAbsent(String id, String firstName, String lastName, String password) {
        if (identityService.createUserQuery().userId(id).count() == 0) {
            User user = identityService.newUser(id);
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setPassword(password);
            identityService.saveUser(user);
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
