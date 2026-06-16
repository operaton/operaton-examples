package org.operaton.examples.bpmnmodelapiparse;

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
        createGroupIfAbsent("managers", "Managers");
        createUserIfAbsent("alice", "Alice", "Smith", "alice@example.com");
        addMemberIfAbsent("alice", "managers");
    }

    private void createGroupIfAbsent(String id, String name) {
        if (identityService.createGroupQuery().groupId(id).count() == 0) {
            Group group = identityService.newGroup(id);
            group.setName(name);
            group.setType("WORKFLOW");
            identityService.saveGroup(group);
        }
    }

    private void createUserIfAbsent(String id, String first, String last, String email) {
        if (identityService.createUserQuery().userId(id).count() == 0) {
            User user = identityService.newUser(id);
            user.setFirstName(first);
            user.setLastName(last);
            user.setEmail(email);
            user.setPassword(id);
            identityService.saveUser(user);
        }
    }

    private void addMemberIfAbsent(String userId, String groupId) {
        if (identityService.createUserQuery().userId(userId).memberOfGroup(groupId).count() == 0) {
            identityService.createMembership(userId, groupId);
        }
    }
}
