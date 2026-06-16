package org.operaton.examples.multitenancy;

import org.operaton.bpm.engine.IdentityService;
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
        createUser("alice", "Alice", "Smith", "alice", "tenantaLegal");
        createUser("bob", "Bob", "Jones", "bob", "tenantbLegal");
    }

    private void createUser(String userId, String first, String last, String password, String groupId) {
        if (identityService.createUserQuery().userId(userId).count() == 0) {
            User user = identityService.newUser(userId);
            user.setFirstName(first);
            user.setLastName(last);
            user.setPassword(password);
            identityService.saveUser(user);
            identityService.createMembership(userId, groupId);
        }
    }
}
