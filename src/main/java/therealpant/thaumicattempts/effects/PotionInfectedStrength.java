package therealpant.thaumicattempts.effects;

import net.minecraft.potion.Potion;

public final class PotionInfectedStrength extends Potion {
    public PotionInfectedStrength() {
        super(false, 0x6f1d87);
        setBeneficial();
        setPotionName("effect.thaumicattempts.infected_strength");
    }

    @Override
    public boolean hasStatusIcon() {
        return false;
    }
}
