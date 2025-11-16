package therealpant.thaumicattempts.client.model;

import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.processor.IBone;
import software.bernie.geckolib3.model.AnimatedGeoModel;
import therealpant.thaumicattempts.golemnet.tile.TileGolemDispatcher;

import java.util.Random;

public class DispatcherModel extends AnimatedGeoModel<TileGolemDispatcher> {

    // группы кубиков для «слоя» по каждой оси (по твоей раскладке pivot’ов)
    private static final int[] GROUP_X_POS = {1, 4, 5, 8}; // x > 0
    private static final int[] GROUP_X_NEG = {2, 3, 6, 7}; // x < 0

    private static final int[] GROUP_Y_POS = {5, 6, 7, 8}; // верхний слой
    private static final int[] GROUP_Y_NEG = {1, 2, 3, 4}; // нижний слой

    private static final int[] GROUP_Z_POS = {3, 4, 7, 8}; // z > 0
    private static final int[] GROUP_Z_NEG = {1, 2, 5, 6}; // z < 0

    @Override
    public ResourceLocation getModelLocation(TileGolemDispatcher obj) {
        return new ResourceLocation("thaumicattempts", "geo/dispatcher.geo.json");
    }

    @Override
    public ResourceLocation getTextureLocation(TileGolemDispatcher obj) {
        return new ResourceLocation("thaumicattempts", "textures/blocks/dispatcher.png");
    }

    @Override
    public ResourceLocation getAnimationFileLocation(TileGolemDispatcher obj) {
        // JSON с анимациями может лежать, но мы его больше не трогаем
        return new ResourceLocation("thaumicattempts", "animations/dispatcher.animation.json");
    }

    @Override
    public void setLivingAnimations(TileGolemDispatcher tile,
                                    Integer uniqueID,
                                    AnimationEvent event) {
        super.setLivingAnimations(tile, uniqueID, event);

        World w = tile.getWorld();
        if (w == null) return;

        long wt = w.getTotalWorldTime();
        long cycLen = tile.cycleLen();
        if (cycLen <= 0) return;

        long cycle = Math.floorDiv(wt, cycLen);
        long inCyc = Math.floorMod(wt, cycLen);

        // новый цикл — обновляем picks/axes для фаз 1–3
        if (cycle != tile.cycleIdx) {
            tile.reseedForCycle(cycle);
        }

        float sp = Math.max(0.3f, tile.animSpeed);
        int base = TileGolemDispatcher.DUR_A + TileGolemDispatcher.GAP;
        long aLen = Math.max(1, Math.round(base / sp)); // длительность одной фазы

        long p1 = aLen;
        long p2 = aLen * 2;
        long p3 = aLen * 3;
        long p4 = aLen * 4; // == cycLen

        // smoothstep по фазе
        java.util.function.BiFunction<Long, Long, Float> phaseK = (t, len) -> {
            // если хочешь – сюда можно вернуть вычитание GAP
            float x = Math.max(0f, Math.min(1f, (float) t / (float) len));
            return x * x * (3f - 2f * x);
        };

        // 1) сбрасываем ВСЕ повороты:
        //    - глобальные "1".."8"
        //    - локальные "1_loc".."8_loc"
        for (int i = 1; i <= 8; i++) {
            IBone global = this.getAnimationProcessor().getBone(String.valueOf(i));
            if (global != null) {
                global.setRotationX(0f);
                global.setRotationY(0f);
                global.setRotationZ(0f);
            }

            IBone local = this.getAnimationProcessor().getBone(i + "_loc");
            if (local != null) {
                local.setRotationX(0f);
                local.setRotationY(0f);
                local.setRotationZ(0f);
            }
        }

        // 2) фазы

        if (inCyc < p1) {
            // фаза 1 – вращаем ОДИН кубик (локальную кость)
            long t = inCyc;
            float k = phaseK.apply(t, aLen);
            rotateLocalPick(tile, 0, (float) Math.toRadians(180f * k));
        } else if (inCyc < p2) {
            // фаза 2
            long t = inCyc - p1;
            float k = phaseK.apply(t, aLen);
            rotateLocalPick(tile, 1, (float) Math.toRadians(180f * k));
        } else if (inCyc < p3) {
            // фаза 3
            long t = inCyc - p2;
            float k = phaseK.apply(t, aLen);
            rotateLocalPick(tile, 2, (float) Math.toRadians(180f * k));
        } else if (inCyc < p4) {
            // фаза 4 – крутим СЛОЙ 2×2 вокруг центра всего массива
            long t = inCyc - p3;
            float k = phaseK.apply(t, aLen);
            rotateSlicePhase4(tile, cycle, k);
        }
    }

    /**
     * Фазы 1–3: один случайный кубик, вращаем локальную кость *_loc
     * вокруг собственного центра.
     */
    private void rotateLocalPick(TileGolemDispatcher tile, int phaseIdx, float radians) {
        int boneIdx = tile.picks[phaseIdx]; // 0..7
        int axis    = tile.axes[phaseIdx];  // 0=X,1=Y,2=Z

        String name = (boneIdx + 1) + "_loc"; // "1_loc".. "8_loc"
        IBone b = this.getAnimationProcessor().getBone(name);
        if (b == null) return;

        if (axis == 0)      b.setRotationX(radians);
        else if (axis == 1) b.setRotationY(radians);
        else                b.setRotationZ(radians);
    }

    /**
     * Фаза 4: выбираем ось (X/Y/Z), сторону (+/-) и знак поворота (+/-180),
     * и вращаем соответствующий слой (4 кубика) как слой кубика Рубика.
     */
    private void rotateSlicePhase4(TileGolemDispatcher tile, long cycle, float k) {
        if (k <= 0f) return;

        long seed = tile.animSeed
                ^ (cycle * 0x9E3779B97F4A7C15L)
                ^ 0xCAFEBABEL;
        Random r = new Random(seed);

        int axisIdx = r.nextInt(3);  // 0=X,1=Y,2=Z
        boolean positiveSide = r.nextBoolean(); // какую половину берём (X+/X-, Y+/Y-, Z+/Z-)
        boolean positiveAngle = r.nextBoolean(); // направление +180 / -180

        float angle = (float) (Math.PI * k * (positiveAngle ? 1f : -1f));

        int[] group;
        switch (axisIdx) {
            case 0: // X
                group = positiveSide ? GROUP_X_POS : GROUP_X_NEG;
                break;
            case 1: // Y
                group = positiveSide ? GROUP_Y_POS : GROUP_Y_NEG;
                break;
            default: // Z
                group = positiveSide ? GROUP_Z_POS : GROUP_Z_NEG;
                break;
        }

        for (int idx : group) {
            IBone global = this.getAnimationProcessor().getBone(String.valueOf(idx));
            if (global == null) continue;

            // ВНИМАНИЕ: крутим ИМЕННО глобальные кости "1".."8",
            // у которых pivot [0, 9.5, 0] — общая ось вращения для слоя.
            if (axisIdx == 0) {
                global.setRotationX(angle);
            } else if (axisIdx == 1) {
                global.setRotationY(angle);
            } else {
                global.setRotationZ(angle);
            }
        }
    }
}
