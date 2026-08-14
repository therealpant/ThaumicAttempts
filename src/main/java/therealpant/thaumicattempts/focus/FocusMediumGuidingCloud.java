package therealpant.thaumicattempts.focus;

import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.casters.FocusMedium;
import thaumcraft.api.casters.FocusNode;
import thaumcraft.api.casters.NodeSetting;
import thaumcraft.api.casters.Trajectory;
import therealpant.thaumicattempts.world.EntityGuidingFirefly;

public final class FocusMediumGuidingCloud extends FocusMedium {
    public static final String KEY = "thaumicattempts.GUIDING_CLOUD";

    @Override
    public String getResearch() {
        return "FOCUSCLOUD";
    }

    @Override
    public String getKey() {
        return KEY;
    }

    @Override
    public Aspect getAspect() {
        return Aspect.MOTION;
    }

    @Override
    public int getComplexity() {
        return 6 + getSettingValue("duration") * 2 + getSettingValue("projectiles") * 5;
    }

    @Override
    public FocusNode.EnumSupplyType[] willSupply() {
        return new FocusNode.EnumSupplyType[]{
                FocusNode.EnumSupplyType.TARGET,
                FocusNode.EnumSupplyType.TRAJECTORY
        };
    }

    @Override
    public boolean execute(Trajectory trajectory) {
        if (trajectory == null || getPackage() == null || getPackage().world == null) return false;

        Vec3d direction = trajectory.direction.normalize();
        Vec3d end = trajectory.source.add(direction.scale(3.0d));
        RayTraceResult hit = getPackage().world.rayTraceBlocks(trajectory.source, end, false, true, false);
        Vec3d fireflyPosition = hit != null && hit.hitVec != null
                ? hit.hitVec.subtract(direction.scale(0.3d))
                : end;

        EntityGuidingFirefly firefly = new EntityGuidingFirefly(
                getRemainingPackage(),
                fireflyPosition,
                getSettingValue("duration"),
                getSettingValue("projectiles"),
                getSettingValue("target") == 3);
        return getPackage().world.spawnEntity(firefly);
    }

    @Override
    public boolean hasIntermediary() {
        return true;
    }

    @Override
    public NodeSetting[] createSettings() {
        return new NodeSetting[]{
                new NodeSetting(
                        "target",
                        "focus.thaumicattempts.guiding_cloud.target",
                        new NodeSetting.NodeSettingIntList(
                                new int[]{2, 3},
                                new String[]{
                                        "focus.projectile.seeking.hostile",
                                        "focus.projectile.seeking.friendly"
                                })),
                new NodeSetting(
                        "duration",
                        "focus.common.duration",
                        new NodeSetting.NodeSettingIntRange(3, 7)),
                new NodeSetting(
                        "projectiles",
                        "focus.thaumicattempts.guiding_cloud.projectiles",
                        new NodeSetting.NodeSettingIntRange(1, 3))
        };
    }

    @Override
    public float getPowerMultiplier() {
        return 0.75f;
    }
}
