package therealpant.thaumicattempts.integration.rift;

/**
 * Server-independent balance model for the Rift Well.
 *
 * <p>The reactor deliberately uses only the number of valid monolith links. A
 * monolith's production rate is still consumed by the visual transport layer,
 * but it cannot change balance or make one particular addon mandatory.</p>
 */
public final class RiftReactor {
    public static final int MAX_DRAINS = 12;
    public static final int MAX_LINKS = 8;
    public static final int MAX_PARTICLE_GROWN_SIZE = 140;
    public static final int MIN_SUSTAINED_SIZE = 5;
    public static final int CLEANSE_TICKS = 600;

    public static final double BASE_SOURCE_SIZE = 50.0D;
    public static final double SOURCE_SIZE_PER_LINK = 11.25D;
    public static final double BASE_COLLECTOR_LIMIT = 60.0D;
    public static final double SIZE_PER_COLLECTOR = 9.0D;
    public static final double COLLECTOR_PULSE_CAPACITY = 6.0D;
    public static final double INSTABILITY_LIMIT = 100.0D;

    public double size;
    /** 0..100 integrity violation. At 100 the controlled shutdown begins. */
    public double tension;
    /** Normalized source activity, retained for client visuals. */
    public double excitation;
    /** Legacy field retained so old NBT can be read without a hard migration. */
    public double wear;
    /** Legacy field; mirrors tension for old status consumers. */
    public double criticalSeconds;
    public boolean critical;
    /** Number of valid monolith links, not particles per second. */
    public double input;
    /** Total energy presented to the circuit by the latest cycle. */
    public double emission;
    /** Energy condensed into paradoxum during the latest cycle. */
    public double absorbed;
    /** Worst of burst occupancy and per-burst collector capacity. */
    public double load;
    /** Fractional progress towards the next whole point of rift size. */
    public double growthBuffer;
    /** Seconds accumulated towards the next global cycle. */
    public double pulseClock;
    /** Kept as a migration alias; shrinking uses the sign of growthBuffer. */
    public double decayBuffer;
    public double collectorRelief;
    public double unrelieved;
    /** Strength of one burst in the latest cycle. */
    public double lastPulseStrength;
    public double damped;
    public double targetSize;
    public double sizeLimit;
    public int rawBurstCount;
    public int pulseSerial;
    public int pulses;
    public int lastArcCount;

    public static double pearlEfficiency(int remaining) {
        return remaining <= 0 ? 0.0D
                : 0.55D + 0.45D * (Math.min(8, remaining) - 1) / 7.0D;
    }

    public static double sourceSize(int links) {
        int count = Math.max(0, Math.min(MAX_LINKS, links));
        return count == 0 ? MIN_SUSTAINED_SIZE
                : BASE_SOURCE_SIZE + SOURCE_SIZE_PER_LINK * count;
    }

    public static double collectorSizeLimit(int collectors) {
        int count = Math.max(0, Math.min(MAX_DRAINS, collectors));
        return Math.min(MAX_PARTICLE_GROWN_SIZE,
                BASE_COLLECTOR_LIMIT + SIZE_PER_COLLECTOR * count);
    }

    public static double equilibriumSize(int links, int collectors) {
        return Math.min(sourceSize(links), collectorSizeLimit(collectors));
    }

    /** One link gives a slow reactor; eight links produce a cycle every eight seconds. */
    public static double eventInterval(int links) {
        return Math.max(8.0D, 20.0D - 1.5D * Math.max(0, Math.min(MAX_LINKS, links)));
    }

    public static int rawBurstCount(int links, double size) {
        int count = Math.max(0, Math.min(MAX_LINKS, links));
        return count == 0 ? 0 : count + (int) Math.ceil(Math.max(0.0D, size) / 18.0D);
    }

    /** A whole pearl suppresses two bursts; damaged pearls contribute fractionally. */
    public static int burstCount(int links, double size, double pearlEfficiencySum) {
        int raw = rawBurstCount(links, size);
        return raw == 0 ? 0 : Math.max(1,
                (int) Math.ceil(raw - 2.0D * Math.max(0.0D, pearlEfficiencySum) - 1.0E-9D));
    }

    /** Collectors broaden the discharge and therefore lower the strength of each burst. */
    public static double burstStrength(int links, double size, int collectors) {
        int source = Math.max(0, Math.min(MAX_LINKS, links));
        int outlets = Math.max(0, Math.min(MAX_DRAINS, collectors));
        return (2.0D + Math.max(0.0D, size) / 25.0D + 0.35D * source)
                / (1.0D + 0.09D * outlets);
    }

    public static double configurationLoad(int bursts, double strength, int collectors) {
        if (bursts <= 0) return 0.0D;
        double occupancy = collectors <= 0 ? bursts : bursts / (double) collectors;
        return Math.max(occupancy, Math.max(0.0D, strength) / COLLECTOR_PULSE_CAPACITY);
    }

    /** Damaged circuits recover increasingly slowly as they approach failure. */
    public double coherence() {
        return Math.max(0.25D, 1.0D - 0.75D * clamp(tension, 0.0D, 100.0D) / 100.0D);
    }

    public interface CycleHandler {
        /** Activates resonant drains and starts the shared visual event. */
        void beginCycle(int burstCount, double burstStrength);
        /** One collector may accept at most one burst in a cycle. */
        double collect(double burstStrength, int burstIndex, int burstCount);
    }

    public void step(int links, int collectors, double pearlEfficiencySum,
                     double seconds, boolean awakening, CycleHandler handler) {
        links = Math.max(0, Math.min(MAX_LINKS, links));
        collectors = Math.max(0, Math.min(MAX_DRAINS, collectors));
        seconds = Math.max(0.0D, seconds);
        input = links;
        excitation = links / (double) MAX_LINKS;
        emission = absorbed = collectorRelief = unrelieved = damped = 0.0D;
        pulses = 0;
        sizeLimit = collectorSizeLimit(collectors);
        targetSize = equilibriumSize(links, collectors);

        if (!awakening && !critical) balanceSize(links, seconds);
        if (awakening || critical || links == 0) {
            pulseClock = 0.0D;
            criticalSeconds = tension;
            return;
        }

        pulseClock += seconds;
        double interval = eventInterval(links);
        for (int event = 0; event < 8 && pulseClock + 1.0E-9D >= interval; event++) {
            pulseClock -= interval;
            emitCycle(links, collectors, pearlEfficiencySum, handler);
            if (critical) break;
        }
        criticalSeconds = tension;
    }

    private void balanceSize(int links, double seconds) {
        double difference = targetSize - size;
        if (Math.abs(difference) < 1.0E-9D) {
            growthBuffer = decayBuffer = 0.0D;
            return;
        }
        if (growthBuffer * difference < 0.0D) growthBuffer = 0.0D;
        double rate = 0.08D + 0.03D * links;
        growthBuffer += Math.copySign(rate * seconds, difference);
        for (int i = 0; i < MAX_PARTICLE_GROWN_SIZE && Math.abs(growthBuffer) >= 1.0D; i++) {
            int direction = growthBuffer > 0.0D ? 1 : -1;
            if ((direction > 0 && size >= targetSize) || (direction < 0 && size <= targetSize)) break;
            size = clamp(size + direction, MIN_SUSTAINED_SIZE, MAX_PARTICLE_GROWN_SIZE);
            growthBuffer -= direction;
        }
        if ((difference > 0 && size >= targetSize) || (difference < 0 && size <= targetSize)) {
            size = targetSize;
            growthBuffer = 0.0D;
        }
        decayBuffer = growthBuffer < 0.0D ? -growthBuffer : 0.0D;
    }

    private void emitCycle(int links, int collectors, double pearlEfficiencySum,
                           CycleHandler handler) {
        rawBurstCount = rawBurstCount(links, size);
        int burstCount = burstCount(links, size, pearlEfficiencySum);
        double strength = burstStrength(links, size, collectors);
        lastArcCount = burstCount;
        lastPulseStrength = strength;
        load = configurationLoad(burstCount, strength, collectors);
        emission = burstCount * strength;
        pulseSerial++;
        pulses++;
        handler.beginCycle(burstCount, strength);

        for (int burst = 0; burst < burstCount; burst++) {
            double accepted = clamp(handler.collect(strength, burst, burstCount), 0.0D, strength);
            absorbed += accepted;
        }
        collectorRelief = absorbed;
        unrelieved = emission <= 0.0D ? 0.0D
                : clamp((emission - absorbed) / emission, 0.0D, 1.0D);

        double missing = 1.0D - (emission <= 0.0D ? 1.0D : absorbed / emission);
        if (load > 1.0D || missing > 1.0E-7D) {
            double overflowBursts = Math.max(0, burstCount - collectors);
            tension += 12.0D + 22.0D * Math.max(0.0D, load - 1.0D)
                    + 2.0D * overflowBursts + 20.0D * missing;
        } else {
            tension -= 0.5D + 8.0D * (1.0D - load);
        }
        tension = clamp(tension, 0.0D, INSTABILITY_LIMIT);
        if (tension >= INSTABILITY_LIMIT) critical = true;
    }

    public double eventProgress() {
        int links = (int) Math.round(input);
        return links <= 0 ? 0.0D : clamp(pulseClock / eventInterval(links), 0.0D, 1.0D);
    }

    public boolean breached() { return critical; }

    public void reset() {
        size = tension = excitation = wear = criticalSeconds = input = emission = absorbed = load = 0.0D;
        growthBuffer = decayBuffer = pulseClock = collectorRelief = unrelieved = lastPulseStrength = 0.0D;
        damped = targetSize = sizeLimit = 0.0D;
        rawBurstCount = pulseSerial = pulses = lastArcCount = 0;
        critical = false;
    }

    public static double clamp(double value, double min, double max) {
        return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : min;
    }
}
