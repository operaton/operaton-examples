package org.operaton.examples.approvalslametrics;

import org.operaton.bpm.engine.IdentityService;
import org.operaton.bpm.engine.identity.Group;
import org.operaton.bpm.engine.identity.User;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements ApplicationRunner {

    private final IdentityService identityService;

    public DataInitializer(IdentityService identityService) {
        this.identityService = identityService;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureGroup("managers", "Managers");
        ensureGroup("directors", "Directors");
        ensureUser("alice", "Alice", "Manager", "alice", "managers");
        ensureUser("bob", "Bob", "Director", "bob", "directors");
    }

    private void ensureGroup(String id, String name) {
        if (identityService.createGroupQuery().groupId(id).count() == 0) {
            Group group = identityService.newGroup(id);
            group.setName(name);
            group.setType("WORKFLOW");
            identityService.saveGroup(group);
        }
    }

    private void ensureUser(String id, String firstName, String lastName, String password, String groupId) {
        if (identityService.createUserQuery().userId(id).count() == 0) {
            User user = identityService.newUser(id);
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setPassword(password);
            identityService.saveUser(user);
            identityService.createMembership(id, groupId);
        }
    }
}
