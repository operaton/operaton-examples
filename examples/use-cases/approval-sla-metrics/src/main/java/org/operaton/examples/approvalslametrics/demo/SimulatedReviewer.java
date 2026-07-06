package org.operaton.examples.approvalslametrics.demo;

import org.operaton.bpm.engine.TaskService;
import org.operaton.bpm.engine.task.Task;
import org.operaton.bpm.engine.variable.Variables;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Demo only: completes open approval tasks after a random age, so some breach their SLA. */
@Component
@ConditionalOnProperty(name = "demo.load-generator.enabled", havingValue = "true")
public class SimulatedReviewer {

    private final TaskService taskService;

    public SimulatedReviewer(TaskService taskService) {
        this.taskService = taskService;
    }

    @Scheduled(fixedDelay = 1000)
    public void reviewPending() {
        List<Task> tasks = taskService.createTaskQuery()
                .taskDefinitionKey("approve-requisition").list();
        long now = System.currentTimeMillis();
        for (Task task : tasks) {
            long ageMs = now - task.getCreateTime().getTime();
            // complete only after a random threshold up to 8s; some tasks age past their SLA first
            if (ageMs > ThreadLocalRandom.current().nextInt(8000)) {
                boolean approved = ThreadLocalRandom.current().nextDouble() < 0.8;
                taskService.complete(task.getId(), Variables.putValue("approved", approved));
            }
        }
    }
}
