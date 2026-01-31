package therealpant.thaumicattempts.capability;

public interface IAmberCasterData {
    float getFrequency();

    void setFrequency(float frequency);

    long getLastUpdateTick();

    void setLastUpdateTick(long tick);

    void tick(long now, boolean hasSet4);

    void recordCast(long now);

    void reset();
}
