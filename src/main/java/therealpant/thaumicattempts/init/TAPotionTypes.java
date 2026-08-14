package therealpant.thaumicattempts.init;

import net.minecraft.init.MobEffects;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.potion.PotionType;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.effects.PotionInfectedStrength;

@Mod.EventBusSubscriber(modid = ThaumicAttempts.MODID)
public final class TAPotionTypes {
    public static final int WARP_WARD_DURATION = 15 * 60 * 20;
    public static final int REGENERATION_DURATION = 30 * 20;

    public static PotionType CLEAR_MIND;
    public static Potion INFECTED_STRENGTH;

    private TAPotionTypes() {}

    @SubscribeEvent
    public static void onRegisterPotions(RegistryEvent.Register<Potion> event) {
        INFECTED_STRENGTH = new PotionInfectedStrength();
        INFECTED_STRENGTH.setRegistryName(ThaumicAttempts.MODID, "infected_strength");
        event.getRegistry().register(INFECTED_STRENGTH);
    }

    @SubscribeEvent
    public static void onRegisterPotionTypes(RegistryEvent.Register<PotionType> event) {
        Potion warpWard = Potion.getPotionFromResourceLocation("thaumcraft:warpWard");
        PotionEffect regen = new PotionEffect(MobEffects.REGENERATION, REGENERATION_DURATION, 0);
        if (warpWard == null) {
            ThaumicAttempts.LOGGER.warn("Warp Ward potion not found; registering Clear Mind without Warp Ward.");
            CLEAR_MIND = new PotionType("clear_mind", regen)
                    .setRegistryName(ThaumicAttempts.MODID, "clear_mind");
        } else {
            PotionEffect ward = new PotionEffect(warpWard, WARP_WARD_DURATION, 0);
            CLEAR_MIND = new PotionType("clear_mind", regen, ward)
                    .setRegistryName(ThaumicAttempts.MODID, "clear_mind");
        }
        event.getRegistry().register(CLEAR_MIND);
    }
}
