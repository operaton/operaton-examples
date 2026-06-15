package org.operaton.examples.processmigration;

import org.operaton.bpm.engine.IdentityService;
import org.operaton.bpm.engine.identity.Group;
import org.operaton.bpm.engine.identity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements ApplicationRunner {

    @Autowired
    private IdentityService identityService;

    @Override
    public void run(ApplicationArguments args) {
        if (identityService.createGroupQuery().groupId("underwriters").count() == 0) {
            Group group = identityService.newGroup("underwriters");
            group.setName("Underwriters");
            identityService.saveGroup(group);
        }
        if (identityService.createUserQuery().userId("alice").count() == 0) {
            User alice = identityService.newUser("alice");
            alice.setFirstName("Alice");
            alice.setLastName("Smith");
            alice.setPassword("alice");
            identityService.saveUser(alice);
            identityService.createMembership("alice", "underwriters");
        }
    }
}
