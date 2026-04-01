package therealpant.thaumicattempts.golemnet.logistics;

import java.util.UUID;

public interface ILogisticsExecutor<T extends RuntimeTask> {
    boolean canAccept(T task);
    boolean submit(T task);
    TaskExecutionSnapshot getSnapshot(UUID taskId);
    void tick();
    boolean accepts(RuntimeTask task);
}