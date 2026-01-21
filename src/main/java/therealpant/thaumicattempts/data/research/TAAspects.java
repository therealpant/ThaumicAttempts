package therealpant.thaumicattempts.data.research;

import net.minecraft.item.ItemStack;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import therealpant.thaumicattempts.golemcraft.ModBlocksItems;
import therealpant.thaumicattempts.init.TABlocks;

public class TAAspects {

    private TAAspects() {}

    public static void register() {

        ThaumcraftApi.registerObjectTag(
                new ItemStack(ModBlocksItems.RIFT_CRISTAL),
                new AspectList()
                        .add(Aspect.FLUX, 25)
                        .add(Aspect.ELDRITCH, 20)
                        .add(Aspect.CRYSTAL, 15)
        );

        ThaumcraftApi.registerObjectTag(
                new ItemStack(ModBlocksItems.RIFT_FLOWER),
                new AspectList()
                        .add(Aspect.FLUX, 25)
                        .add(Aspect.ELDRITCH, 20)
                        .add(Aspect.PLANT, 15)
        );

        ThaumcraftApi.registerObjectTag(
                new ItemStack(ModBlocksItems.RIFT_STONE),
                new AspectList()
                        .add(Aspect.FLUX, 25)
                        .add(Aspect.ELDRITCH, 20)
                        .add(Aspect.EARTH, 15)
        );


        ThaumcraftApi.registerObjectTag(
                new ItemStack(ModBlocksItems.MIND_FRUIT),
                new AspectList()
                        .add(Aspect.AURA, 15)
                        .add(Aspect.MIND, 10)
                        .add(Aspect.ORDER, 10)
        );

        ThaumcraftApi.registerObjectTag(
                new ItemStack(ModBlocksItems.MATURE_MIND_FRUIT),
                new AspectList()
                        .add(Aspect.AURA, 30)
                        .add(Aspect.MIND, 15)
                        .add(Aspect.ORDER, 20)
        );

        ThaumcraftApi.registerObjectTag(
                new ItemStack(ModBlocksItems.TAINTED_MIND_FRUIT),
                new AspectList()
                        .add(Aspect.FLUX, 20)
                        .add(Aspect.ELDRITCH, 15)
                        .add(Aspect.PLANT, 15)
        );


        ThaumcraftApi.registerObjectTag(
                new ItemStack(ModBlocksItems.ANOMALY_SEEDS),
                new AspectList()
                        .add(Aspect.EXCHANGE, 20)
                        .add(Aspect.ELDRITCH, 15)
                        .add(Aspect.PLANT, 15)
        );


        ThaumcraftApi.registerObjectTag(
                new ItemStack(TABlocks.RIFT_GEOD),
                new AspectList()
                        .add(Aspect.FLUX, 30)
                        .add(Aspect.ELDRITCH, 15)
                        .add(Aspect.CRYSTAL, 30)
                        .add(Aspect.EARTH, 20)
        );
        ThaumcraftApi.registerObjectTag(
                new ItemStack(TABlocks.ANOMALY_STONE),
                new AspectList()
                        .add(Aspect.FLUX, 30)
                        .add(Aspect.ELDRITCH, 15)
                        .add(Aspect.DARKNESS, 20)
                        .add(Aspect.EARTH, 20)
        );
        ThaumcraftApi.registerObjectTag(
                new ItemStack(TABlocks.RIFT_BUSH),
                new AspectList()
                        .add(Aspect.FLUX, 30)
                        .add(Aspect.ELDRITCH, 15)
                        .add(Aspect.DARKNESS, 20)
                        .add(Aspect.PLANT, 20)
        );
    }
}
