package therealpant.thaumicattempts.capability;

import therealpant.thaumicattempts.effects.AmberEffects;

public class AmberCasterData implements IAmberCasterData {
    private float frequency;
    private long lastUpdateTick;

    @Override
    public float getFrequency() {
        return frequency;
    }

    @Override
    public void setFrequency(float frequency) {
        this.frequency = frequency;
    }

    @Override
    public long getLastUpdateTick() {
        return lastUpdateTick;
    }

    @Override
    public void setLastUpdateTick(long tick) {
        this.lastUpdateTick = tick;
    }

    @Override
    public void tick(long now, boolean hasSet4) {
        if (!hasSet4) {
            reset();
            lastUpdateTick = now;
            return;
        }
        if (lastUpdateTick == 0L) {
            lastUpdateTick = now;
            return;
        }
        long delta = now - lastUpdateTick;
        if (delta <= 0L) return;
        float decay = delta * AmberEffects.SET4_FREQUENCY_DECAY_PER_TICK;
        frequency = Math.max(0f, frequency - decay);
        lastUpdateTick = now;
    }

    @Override
    public void recordCast(long now) {
        tick(now, true);
        frequency = Math.min(AmberEffects.SET4_FREQUENCY_MAX, frequency + AmberEffects.SET4_FREQUENCY_INCREMENT);
        lastUpdateTick = now;
    }

    @Override
    public void reset() {
        frequency = 0f;
        lastUpdateTick = 0L;
    }
}