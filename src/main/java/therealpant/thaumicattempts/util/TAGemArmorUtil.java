package therealpant.thaumicattempts.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

public final class TAGemArmorUtil {
    private static final EntityEquipmentSlot[] ARMOR_SLOTS = {
            EntityEquipmentSlot.HEAD,
            EntityEquipmentSlot.CHEST,
            EntityEquipmentSlot.LEGS,
            EntityEquipmentSlot.FEET
    };

    private TAGemArmorUtil() {}

    public static List<GemInlay> getEquippedGemInlays(EntityPlayer player) {
        if (player == null) return Collections.emptyList();
        List<GemInlay> inlays = new ArrayList<>();
        for (EntityEquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack armor = player.getItemStackFromSlot(slot);
            if (armor.isEmpty()) continue;
            if (!TAGemInlayUtil.hasGem(armor)) continue;
            ResourceLocation id = TAGemInlayUtil.getGemId(armor);
            if (id == null) continue;
            int tier = TAGemInlayUtil.getTier(armor);
            int damage = TAGemInlayUtil.getDamage(armor);
            inlays.add(new GemInlay(armor, id, tier, damage));
        }
        return inlays;
    }

    public static final class GemInlay {
        private final ItemStack stack;
        private final ResourceLocation id;
        private final int tier;
        private final int damage;

        private GemInlay(ItemStack stack, ResourceLocation id, int tier, int damage) {
            this.stack = stack;
            this.id = id;
            this.tier = tier;
            this.damage = damage;
        }

        public ItemStack getStack() {
            return stack;
        }

        public ResourceLocation getId() {
            return id;
        }

        public int getTier() {
            return tier;
        }

        public int getDamage() {
            return damage;
        }
    }
}
