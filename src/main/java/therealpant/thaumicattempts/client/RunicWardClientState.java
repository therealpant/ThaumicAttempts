package therealpant.thaumicattempts.client;

public final class RunicWardClientState {
    private static boolean active;
    private static float current;
    private static int max;
    private static float fortification;

    private RunicWardClientState() {}

    public static void update(boolean activeValue, float currentValue, int maxValue, float fortificationValue) {
        active = activeValue;
        current = Math.max(0f, currentValue);
        max = Math.max(0, maxValue);
        fortification = Math.max(0f, fortificationValue);
    }

    public static boolean isActive() { return active; }
    public static float getCurrent() { return current; }
    public static int getMax() { return max; }
    public static float getFortification() { return fortification; }
}
