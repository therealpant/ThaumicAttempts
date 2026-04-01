package therealpant.thaumicattempts.golemnet.logistics;

import java.util.UUID;

public class TaskExecutionSnapshot {
    public final UUID taskId;
    public final TaskStatus status;
    public final long completedAmount;

    public TaskExecutionSnapshot(UUID taskId, TaskStatus status, long completedAmount) {
        this.taskId = taskId;
        this.status = status;
        this.completedAmount = completedAmount;
    }
}