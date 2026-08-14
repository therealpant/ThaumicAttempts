package therealpant.thaumicattempts.focus;

import net.minecraft.util.ResourceLocation;
import thaumcraft.api.casters.FocusEngine;
import therealpant.thaumicattempts.ThaumicAttempts;

public final class TAFocusElements {
    private static boolean registered;

    private TAFocusElements() {}

    public static void register() {
        if (registered) return;
        registered = true;
        FocusEngine.registerElement(
                FocusMediumGuidingCloud.class,
                new ResourceLocation("thaumcraft", "textures/foci/spellbat.png"),
                0x8A5AC7);
        ThaumicAttempts.LOGGER.info("Registered Guiding Firefly focus medium");
    }
}
