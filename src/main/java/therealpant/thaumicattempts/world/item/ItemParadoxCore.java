package therealpant.thaumicattempts.world.item;

import net.minecraft.item.ItemStack;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.util.NonNullList;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;

import javax.annotation.Nullable;
import java.util.List;

public class ItemParadoxCore extends ItemRiftAmethyst {
    public static final int MAX_PARADOXUM = 2500;

    public ItemParadoxCore() {
        setMaxStackSize(1);
    }

    @Override
    public int getParadoxumCapacity(ItemStack stack) {
        return MAX_PARADOXUM;
    }

    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
        if (!isInCreativeTab(tab)) return;
        items.add(new ItemStack(this));
        ItemStack charged = new ItemStack(this);
        ParadoxumContainer.add(charged, MAX_PARADOXUM);
        items.add(charged);
    }

    @Override
    public AspectList getAspects(ItemStack stack) {
        AspectList inherited = super.getAspects(stack);
        AspectList filtered = new AspectList();
        if (inherited != null) {
            for (Aspect aspect : inherited.getAspects()) {
                if (aspect != null && aspect != therealpant.thaumicattempts.data.research.TAAspects.PARADOXUM) {
                    filtered.add(aspect, inherited.getAmount(aspect));
                }
            }
        }
        return filtered;
    }

    @Override
    public void setAspects(ItemStack stack, AspectList aspects) {
        AspectList filtered = new AspectList();
        if (aspects != null) {
            for (Aspect aspect : aspects.getAspects()) {
                if (aspect != null && aspect != therealpant.thaumicattempts.data.research.TAAspects.PARADOXUM) {
                    filtered.add(aspect, aspects.getAmount(aspect));
                }
            }
        }
        super.setAspects(stack, filtered);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world,
                               List<String> tooltip, ITooltipFlag flag) {
        tooltip.add(TextFormatting.LIGHT_PURPLE + new TextComponentTranslation(
                "tooltip.thaumicattempts.paradoxum_charge",
                ParadoxumContainer.getStored(stack), MAX_PARADOXUM).getFormattedText());
    }
}
