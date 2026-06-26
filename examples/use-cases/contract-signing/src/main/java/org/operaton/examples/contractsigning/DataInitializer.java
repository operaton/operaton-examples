package org.operaton.examples.contractsigning;

import org.operaton.bpm.engine.IdentityService;
import org.operaton.bpm.engine.identity.Group;
import org.operaton.bpm.engine.identity.User;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class DataInitializer {
    private final IdentityService identityService;

    public DataInitializer(IdentityService identityService) { this.identityService = identityService; }

    @PostConstruct
    public void init() {
        createGroupIfNotExists("customers", "Customer group");
        createGroupIfNotExists("legal", "Legal review group");
        createUserInGroupIfNotExists("alice", "alice", "Alice", "customers");
        createUserInGroupIfNotExists("bob", "bob", "Bob", "legal");
    }

    private void createGroupIfNotExists(String groupId, String groupName) {
        if (identityService.createGroupQuery().groupId(groupId).count() == 0) {
            Group group = identityService.newGroup(groupId);
            group.setName(groupName);
            identityService.saveGroup(group);
        }
    }

    private void createUserInGroupIfNotExists(String userId, String password, String firstName, String groupId) {
        if (identityService.createUserQuery().userId(userId).count() == 0) {
            User user = identityService.newUser(userId);
            user.setPassword(password);
            user.setFirstName(firstName);
            identityService.saveUser(user);
            identityService.createMembership(userId, groupId);
        }
    }
}
