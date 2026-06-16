package org.operaton.examples.inclusivegateway;

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
        createGroupIfAbsent("medicalTeam", "Medical Team");
        createGroupIfAbsent("propertyTeam", "Property Team");
        createUserIfAbsent("alice", "Alice", "Medic", "alice@example.com");
        createUserIfAbsent("bob", "Bob", "Adjuster", "bob@example.com");
        addMemberIfAbsent("alice", "medicalTeam");
        addMemberIfAbsent("bob", "propertyTeam");
    }

    private void createGroupIfAbsent(String id, String name) {
        if (identityService.createGroupQuery().groupId(id).singleResult() == null) {
            Group group = identityService.newGroup(id);
            group.setName(name);
            group.setType("WORKFLOW");
            identityService.saveGroup(group);
        }
    }

    private void createUserIfAbsent(String id, String firstName, String lastName, String email) {
        if (identityService.createUserQuery().userId(id).singleResult() == null) {
            User user = identityService.newUser(id);
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setEmail(email);
            identityService.saveUser(user);
        }
    }

    private void addMemberIfAbsent(String userId, String groupId) {
        boolean isMember = !identityService.createUserQuery()
            .memberOfGroup(groupId).userId(userId).list().isEmpty();
        if (!isMember) {
            identityService.createMembership(userId, groupId);
        }
    }
}
