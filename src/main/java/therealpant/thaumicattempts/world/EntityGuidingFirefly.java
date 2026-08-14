package therealpant.thaumicattempts.world;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import net.minecraft.entity.EntityFlying;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import thaumcraft.api.casters.FocusEffect;
import thaumcraft.api.casters.FocusEngine;
import thaumcraft.api.casters.FocusPackage;
import thaumcraft.api.casters.Trajectory;
import thaumcraft.common.entities.projectile.EntityFocusProjectile;
import thaumcraft.common.lib.utils.EntityUtils;

/**
 * A short-lived spell creature which orbits its summoning point and launches
 * vanilla TC6 focus projectiles. It deliberately has no relationship with
 * EntityFocusCloud, so the stock Cloud medium remains completely untouched.
 */
public final class EntityGuidingFirefly extends EntityFlying {
    private static final DataParameter<Integer> FOCUS_COLOR = EntityDataManager.createKey(
            EntityGuidingFirefly.class,
            DataSerializers.VARINT);
    private static final DataParameter<Integer> PROJECTILES_PER_ATTACK = EntityDataManager.createKey(
            EntityGuidingFirefly.class,
            DataSerializers.VARINT);
    private static final int DEFAULT_FOCUS_COLOR = 0x8A5AC7;
    private static final double TARGET_RANGE = 16.0d;
    // One stock TC6 projectile-speed setting step is 1/3 block per tick.
    private static final float PROJECTILE_SPEED = 0.60f + 1.0f / 3.0f;
    private static final int ATTACK_INTERVAL_TICKS = 20;
    private static final int SHOT_INTERVAL_TICKS = 6;
    private static final int FLIGHT_PATTERN_COUNT = 5;

    private FocusPackage shotPackage;
    private int durationTicks = 60;
    private int projectilesPerAttack = 1;
    private int nextAttackTick = ATTACK_INTERVAL_TICKS;
    private int patternCursor;
    private int volleyIndex;
    private final int[] queuedTargetIds = {-1, -1, -1};
    private int queuedShotCount;
    private int queuedShotIndex;
    private int nextQueuedShotTick;
    private boolean targetFriendly;
    private double anchorX;
    private double anchorY;
    private double anchorZ;
    private float orbitPhase;

    public EntityGuidingFirefly(World world) {
        super(world);
        setSize(0.30f, 0.30f);
        setNoGravity(true);
        setNoAI(true);
        setSilent(true);
        setEntityInvulnerable(true);
        noClip = true;
        enablePersistence();
    }

    public EntityGuidingFirefly(FocusPackage focusPackage,
                                Vec3d anchor,
                                int durationSeconds,
                                int projectilesPerAttack,
                                boolean targetFriendly) {
        this(focusPackage.world);
        this.shotPackage = focusPackage;
        setFocusColor(calculateFocusColor(focusPackage));
        this.durationTicks = Math.max(1, durationSeconds) * 20;
        setProjectilesPerAttack(projectilesPerAttack);
        this.targetFriendly = targetFriendly;
        this.anchorX = anchor.x;
        this.anchorY = anchor.y;
        this.anchorZ = anchor.z;
        this.orbitPhase = rand.nextFloat() * ((float) Math.PI * 2.0f);
        setPosition(anchorX + Math.cos(orbitPhase) * 0.75d,
                anchorY + 0.15d,
                anchorZ + Math.sin(orbitPhase) * 0.75d);
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataManager.register(FOCUS_COLOR, DEFAULT_FOCUS_COLOR);
        dataManager.register(PROJECTILES_PER_ATTACK, 1);
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(1.0d);
        getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.1d);
    }

    @Override
    public void onUpdate() {
        noClip = true;
        setNoGravity(true);
        super.onUpdate();

        if (world.isRemote) return;

        EntityLivingBase owner = getOwner();
        if (owner == null || !owner.isEntityAlive() || ticksExisted > durationTicks) {
            setDead();
            return;
        }

        updateOrbit(owner);

        if (queuedShotIndex < queuedShotCount && ticksExisted >= nextQueuedShotTick) {
            launchQueuedShot(owner);
        }

        // A volley is selected once and then emitted at six-tick intervals.
        if (queuedShotIndex < queuedShotCount) return;

        if (ticksExisted < nextAttackTick) return;
        List<EntityLivingBase> targets = findTargets(owner);
        if (targets.isEmpty()) {
            nextAttackTick = ticksExisted + 5;
            return;
        }

        queuedShotCount = projectilesPerAttack;
        queuedShotIndex = 0;
        for (int i = 0; i < queuedShotCount; i++) {
            queuedTargetIds[i] = targets.get((volleyIndex + i) % targets.size()).getEntityId();
        }
        volleyIndex++;
        nextAttackTick = ticksExisted + ATTACK_INTERVAL_TICKS;
        nextQueuedShotTick = ticksExisted;
        launchQueuedShot(owner);
    }

    private void launchQueuedShot(EntityLivingBase owner) {
        if (queuedShotIndex >= queuedShotCount) return;

        Entity entity = world.getEntityByID(queuedTargetIds[queuedShotIndex]);
        EntityLivingBase target = entity instanceof EntityLivingBase
                && entity.isEntityAlive()
                ? (EntityLivingBase) entity
                : findReplacementTarget(owner);
        if (target != null) {
            launchProjectile(owner, target, patternCursor++ % FLIGHT_PATTERN_COUNT);
        }
        queuedShotIndex++;
        nextQueuedShotTick = ticksExisted + SHOT_INTERVAL_TICKS;
    }

    private EntityLivingBase findReplacementTarget(EntityLivingBase owner) {
        List<EntityLivingBase> targets = findTargets(owner);
        return targets.isEmpty() ? null : targets.get(0);
    }

    private void updateOrbit(EntityLivingBase owner) {
        double followX = owner.posX;
        double followY = owner.posY + owner.height + 0.65d;
        double followZ = owner.posZ;

        // Keep the orbit centred on the owner. After teleport-like movement,
        // snap the anchor close to the player instead of crossing the world.
        double anchorDistanceSq = (anchorX - followX) * (anchorX - followX)
                + (anchorY - followY) * (anchorY - followY)
                + (anchorZ - followZ) * (anchorZ - followZ);
        if (anchorDistanceSq > 64.0d) {
            anchorX = followX;
            anchorY = followY;
            anchorZ = followZ;
            setPosition(followX, followY, followZ);
            motionX = motionY = motionZ = 0.0d;
        } else {
            anchorX += (followX - anchorX) * 0.24d;
            anchorY += (followY - anchorY) * 0.24d;
            anchorZ += (followZ - anchorZ) * 0.24d;
        }

        double angle = orbitPhase + ticksExisted * 0.085d;
        double radius = 0.82d + Math.sin(ticksExisted * 0.047d + orbitPhase) * 0.22d;
        double targetX = anchorX + Math.cos(angle) * radius;
        double targetY = anchorY + 0.18d + Math.sin(angle * 1.7d + orbitPhase) * 0.32d;
        double targetZ = anchorZ + Math.sin(angle) * radius;

        motionX = motionX * 0.62d + (targetX - posX) * 0.11d;
        motionY = motionY * 0.62d + (targetY - posY) * 0.11d;
        motionZ = motionZ * 0.62d + (targetZ - posZ) * 0.11d;
        velocityChanged = true;
    }

    private EntityLivingBase getOwner() {
        return shotPackage == null ? null : shotPackage.getCaster();
    }

    /** Uses the same averaged effect-element colour rule as a TC6 focus item. */
    private static int calculateFocusColor(FocusPackage focusPackage) {
        if (focusPackage == null) return DEFAULT_FOCUS_COLOR;
        FocusEffect[] effects = focusPackage.getFocusEffects();
        if (effects == null || effects.length == 0) return DEFAULT_FOCUS_COLOR;

        long red = 0L;
        long green = 0L;
        long blue = 0L;
        int count = 0;
        for (FocusEffect effect : effects) {
            if (effect == null) continue;
            int color = FocusEngine.getElementColor(effect.getKey());
            red += color >> 16 & 0xFF;
            green += color >> 8 & 0xFF;
            blue += color & 0xFF;
            count++;
        }
        if (count == 0) return DEFAULT_FOCUS_COLOR;
        return ((int) (red / count) << 16)
                | ((int) (green / count) << 8)
                | (int) (blue / count);
    }

    private void setFocusColor(int color) {
        dataManager.set(FOCUS_COLOR, color & 0xFFFFFF);
    }

    public int getFocusColor() {
        return dataManager.get(FOCUS_COLOR);
    }

    private void setProjectilesPerAttack(int count) {
        projectilesPerAttack = Math.max(1, Math.min(3, count));
        dataManager.set(PROJECTILES_PER_ATTACK, projectilesPerAttack);
    }

    public int getProjectilesPerAttack() {
        return dataManager.get(PROJECTILES_PER_ATTACK);
    }

    private List<EntityLivingBase> findTargets(EntityLivingBase owner) {
        AxisAlignedBB area = getEntityBoundingBox().grow(TARGET_RANGE);
        List<EntityLivingBase> targets = world.getEntitiesWithinAABB(
                EntityLivingBase.class,
                area,
                target -> target != null
                        && target != this
                        && target != owner
                        && target.isEntityAlive()
                        && target.canBeAttackedWithItem()
                        && EntityUtils.isFriendly(owner, target) == targetFriendly
                        && EntityUtils.canEntityBeSeen(this, target));
        targets.sort(Comparator.comparingDouble(this::getDistanceSq));
        return targets;
    }

    private void launchProjectile(EntityLivingBase owner, EntityLivingBase target, int pattern) {
        if (shotPackage == null) return;

        Vec3d sourceCenter = getPositionVector().add(0.0d, height * 0.5d, 0.0d);
        Vec3d targetCenter = target.getPositionVector().add(0.0d, target.height * 0.55d, 0.0d);
        Vec3d delta = targetCenter.subtract(sourceCenter);
        Vec3d horizontal = new Vec3d(delta.x, 0.0d, delta.z);
        if (horizontal.lengthSquared() < 1.0e-6d) horizontal = new Vec3d(0.0d, 0.0d, 1.0d);
        horizontal = horizontal.normalize();
        Vec3d side = new Vec3d(-horizontal.z, 0.0d, horizontal.x);

        // Aim close to the selected target rather than launching upward first.
        // The five patterns start centrally, right, left, low and high.
        double[] lateralPatterns = {0.0d, 0.45d, -0.45d, 0.0d, 0.0d};
        double[] verticalPatterns = {0.0d, 0.0d, 0.0d, -0.32d, 0.32d};
        double lateral = lateralPatterns[pattern] * Math.max(0.45d, target.width * 0.65d);
        double vertical = verticalPatterns[pattern] * Math.max(0.65d, target.height * 0.45d);
        Vec3d source = sourceCenter.add(side.scale(lateralPatterns[pattern] * 0.08d));
        Vec3d approximateTarget = targetCenter.add(side.scale(lateral)).add(0.0d, vertical, 0.0d);
        Vec3d direction = approximateTarget.subtract(source).normalize();

        FocusPackage projectilePackage = shotPackage.copy(owner);
        projectilePackage.setUniqueID(UUID.randomUUID());
        EntityFocusProjectile projectile = new EntityFocusProjectile(
                projectilePackage,
                PROJECTILE_SPEED,
                new Trajectory(source, direction),
                targetFriendly ? 3 : 2);
        projectile.setPosition(source.x, source.y, source.z);
        world.spawnEntity(projectile);
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean canBePushed() {
        return false;
    }

    @Override
    protected boolean canDespawn() {
        return false;
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setInteger("DurationTicks", durationTicks);
        tag.setInteger("ProjectilesPerAttack", projectilesPerAttack);
        tag.setInteger("NextAttackTick", nextAttackTick);
        tag.setInteger("PatternCursor", patternCursor);
        tag.setInteger("VolleyIndex", volleyIndex);
        tag.setBoolean("TargetFriendly", targetFriendly);
        tag.setDouble("AnchorX", anchorX);
        tag.setDouble("AnchorY", anchorY);
        tag.setDouble("AnchorZ", anchorZ);
        tag.setFloat("OrbitPhase", orbitPhase);
        tag.setInteger("FocusColor", getFocusColor());
        if (shotPackage != null) tag.setTag("ShotPackage", shotPackage.serialize());
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        durationTicks = Math.max(1, tag.getInteger("DurationTicks"));
        setProjectilesPerAttack(tag.getInteger("ProjectilesPerAttack"));
        nextAttackTick = Math.max(1, tag.getInteger("NextAttackTick"));
        patternCursor = Math.max(0, tag.getInteger("PatternCursor"));
        volleyIndex = Math.max(0, tag.getInteger("VolleyIndex"));
        targetFriendly = tag.getBoolean("TargetFriendly");
        anchorX = tag.getDouble("AnchorX");
        anchorY = tag.getDouble("AnchorY");
        anchorZ = tag.getDouble("AnchorZ");
        orbitPhase = tag.getFloat("OrbitPhase");
        if (tag.hasKey("FocusColor", 3)) setFocusColor(tag.getInteger("FocusColor"));
        if (tag.hasKey("ShotPackage", 10)) {
            shotPackage = new FocusPackage();
            shotPackage.deserialize(tag.getCompoundTag("ShotPackage"));
            if (!tag.hasKey("FocusColor", 3)) setFocusColor(calculateFocusColor(shotPackage));
        }
    }
}
