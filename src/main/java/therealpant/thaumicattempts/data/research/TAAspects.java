package therealpant.thaumicattempts.data.research;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.init.ModBlocksItems;
import therealpant.thaumicattempts.init.TABlocks;
import therealpant.thaumicattempts.world.item.ItemRiftAmethyst;

public class TAAspects {
    public static Aspect PARADOXUM;

    private static final String PARADOXUM_TAG = "paradoxum";
    private static final int PARADOXUM_COLOR = 0x020A3A;
    private static final ResourceLocation PARADOXUM_ICON = new ResourceLocation(
            ThaumicAttempts.MODID,
            "textures/asspects/paradoxs.png"
    );

    private TAAspects() {}

    public static void registerAspects() {
        Aspect existing = Aspect.getAspect(PARADOXUM_TAG);
        PARADOXUM = existing != null
                ? existing
                : new Aspect(PARADOXUM_TAG, PARADOXUM_COLOR, null, PARADOXUM_ICON, 1);
    }

    public static void register() {
        registerAspects();

        ThaumcraftApi.registerObjectTag(
                new ItemStack(ModBlocksItems.RIFT_CRISTAL),
                new AspectList()
                        .add(Aspect.FLUX, 25)
                        .add(Aspect.ELDRITCH, 20)
                        .add(Aspect.CRYSTAL, 15)
        );

        ThaumcraftApi.registerObjectTag(
                new ItemStack(ModBlocksItems.RIFT_AMETIST),
                ItemRiftAmethyst.createBaseAspects()
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

        // The four well-forged protocol cores share a crystalline Paradoxum
        // matrix, while the dominant aspects describe their intended purpose.
        ThaumcraftApi.registerObjectTag(
                new ItemStack(ModBlocksItems.NAVIGATION_CORE),
                new AspectList()
                        .add(Aspect.MOTION, 25)
                        .add(Aspect.VOID, 20)
                        .add(Aspect.MIND, 15)
                        .add(Aspect.CRYSTAL, 10)
                        .add(PARADOXUM, 10)
        );

        ThaumcraftApi.registerObjectTag(
                new ItemStack(ModBlocksItems.TRANSMUTATION_CORE),
                new AspectList()
                        .add(Aspect.EXCHANGE, 30)
                        .add(Aspect.MAGIC, 20)
                        .add(Aspect.ENTROPY, 15)
                        .add(Aspect.CRYSTAL, 10)
                        .add(PARADOXUM, 10)
        );

        ThaumcraftApi.registerObjectTag(
                new ItemStack(ModBlocksItems.CREATION_CORE),
                new AspectList()
                        .add(Aspect.CRAFT, 30)
                        .add(Aspect.LIFE, 20)
                        .add(Aspect.MAGIC, 15)
                        .add(Aspect.CRYSTAL, 10)
                        .add(PARADOXUM, 10)
        );

        // Alchemical cores store wear in metadata, so every hidden wear state
        // needs the same explicit object tag for scanning and essentia lookup.
        for (int wear = 0; wear < therealpant.thaumicattempts.items.ItemAlchemicalCore.USES; wear++) {
            ThaumcraftApi.registerObjectTag(
                    new ItemStack(ModBlocksItems.ALCHEMICAL_CORE, 1, wear),
                    new AspectList()
                            .add(Aspect.ALCHEMY, 25)
                            .add(Aspect.EXCHANGE, 20)
                            .add(Aspect.MAGIC, 15)
                            .add(Aspect.CRYSTAL, 10)
                            .add(PARADOXUM, 10)
            );
        }

        ThaumcraftApi.registerObjectTag(
                new ItemStack(ModBlocksItems.ARCHIVE_FRAGMENTS),
                new AspectList()
                        .add(Aspect.ELDRITCH, 25)
                        .add(Aspect.MIND, 20)
                        .add(Aspect.MAGIC, 15)
                        .add(Aspect.VOID, 10)
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
