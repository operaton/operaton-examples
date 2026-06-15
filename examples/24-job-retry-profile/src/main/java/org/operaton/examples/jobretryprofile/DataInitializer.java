package org.operaton.examples.jobretryprofile;

import org.operaton.bpm.engine.IdentityService;
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
        if (identityService.createUserQuery().userId("alice").count() == 0) {
            User alice = identityService.newUser("alice");
            alice.setFirstName("Alice");
            alice.setLastName("Smith");
            alice.setPassword("alice");
            identityService.saveUser(alice);
        }
    }
}
