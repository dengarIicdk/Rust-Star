package dev.starport;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * A straight-line orbital rocket entity.
 *
 * <p>It uses the Rust core for two things and nothing else:
 * <ul>
 *   <li>tuning validation when the rocket is launched;</li>
 *   <li>a swept collision check each tick that decides whether the next
 *       position is still inside the world and whether it hit anything.</li>
 * </ul>
 *
 * <p>The authoritative flight state is this entity's position, which lives in
 * the server world (standard Minecraft simulation). Rust is consulted per tick
 * for the <i>decision</i> (valid tuning / hit / out-of-world), not per block.
 */
public final class RocketEntity extends ProjectileEntity {
    private static final TrackedData<Integer> OWNER_ID = DataTracker.registerData(
        RocketEntity.class, TrackedDataHandlerRegistry.INTEGER);

    /** Height above the build limit where the strike spawns. */
    public static final int ORBIT_HEIGHT = 180;
    /** Descent speed in blocks per tick; fast enough to read as a strike. */
    public static final double ORBIT_SPEED = 6.0;

    private StrikeTuning tuning = StrikeTuning.DEFAULT;
    private int age;
    private boolean exploded;
    /** Y the projectile started at, so the descent distance is known. */
    private double descendFrom;

    public RocketEntity(EntityType<? extends ProjectileEntity> type, World world) {
        super(type, world);
    }

    /**
     * Creates a strike arriving from orbit onto {@code target}.
     *
     * <p>The projectile does not travel from the player: it appears high above
     * the marked point and falls straight down, which is how an orbital strike
     * reads. The player only chooses the column.
     */
    public static RocketEntity createFromOrbit(
            ServerWorld world, ServerPlayerEntity owner, Vec3d target, StrikeTuning in) {
        StrikeTuning tuning = in.orDefault();
        if (!isFinite(target)) {
            return null;
        }
        RocketEntity r = new RocketEntity(ModEntities.ORBITAL_ROCKET, world);
        // Start at the top of the world so the fall is visible, and aim
        // straight down. `descendFrom` lets the entity know when to vanish.
        double top = world.getTopY() + ORBIT_HEIGHT;
        Vec3d start = new Vec3d(target.x, top, target.z);
        r.setPosition(start);
        r.setVelocity(0.0, -ORBIT_SPEED, 0.0);
        r.descendFrom = top;
        r.tuning = tuning;
        if (owner != null) {
            r.setOwner(owner);
            r.getDataTracker().set(OWNER_ID, owner.getId());
        }
        return r;
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        // Entity declares this abstract: there is no super implementation.
        builder.add(OWNER_ID, -1);
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) {
            // Smoke trail while flying. Client-side keeps it smooth and costs
            // the server nothing.
            // Several flames around the body so the trail has width, not a
            // single thin line.
            for (int i = 0; i < 6; i++) {
                double a = getWorld().random.nextDouble() * Math.PI * 2;
                double off = getWorld().random.nextDouble() * 1.6;
                getWorld().addParticle(net.minecraft.particle.ParticleTypes.FLAME,
                    getX() + Math.cos(a) * off, getY(), getZ() + Math.sin(a) * off, 0.0, 0.0, 0.0);
            }
            getWorld().addParticle(net.minecraft.particle.ParticleTypes.CAMPFIRE_COSY_SMOKE,
                getX(), getY(), getZ(), 0.0, 0.01, 0.0);
            return;
        }
        age++;
        if (age > tuning.lifetimeTicks()) {
            discard();
            return;
        }

        Vec3d pos = getPos();
        Vec3d vel = getVelocity();
        double spd = vel.length();
        if (spd < 1.0e-9) {
            discard();
            return;
        }

        // 1. Swept collision against the world (blocks). The beam falls
        //    straight down, so a miss means "nothing hit yet, keep falling".
        RaycastContext ctx = new RaycastContext(
            pos, pos.add(vel), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this);
        BlockHitResult hit = getWorld().raycast(ctx);
        if (hit.getType() != HitResult.Type.MISS) {
            onImpact(hit.getPos().x, hit.getPos().y, hit.getPos().z);
            return;
        }

        // 2. Out-of-world check.
        if (pos.getY() < getWorld().getBottomY() - 16 || pos.getY() > getWorld().getTopY() + 64) {
            discard();
            return;
        }

        // 3. Advance. The Rust swept-AABB math is intentionally NOT called per
        //    tick for the block raycast (Minecraft's own traversal is more
        //    accurate and runs in native code); we use Rust for the beam plan
        //    and validation instead, where its determinism matters.
        setPosition(pos.add(vel));
    }

    private void onImpact(double x, double y, double z) {
        if (exploded) {
            return;
        }
        exploded = true;
        if (!getWorld().isClient) {
            ServerWorld serverWorld = (ServerWorld) getWorld();
            StrikeManager.queueBeam(serverWorld, getOwnerUuid(), x, z, tuning.radius(),
                serverWorld.getBottomY(), serverWorld.getTopY() - 1);
            // Impact sound, broadcast to everyone nearby. Volume is raised well
            // past 1.0 because a strike is meant to be heard across a valley;
            // Minecraft allows amplitude above 1 for exactly this.
            serverWorld.playSound(null, BlockPos.ofFloored(x, y, z),
                SoundEvent.of(Identifier.of(StarflightMod.MOD_ID, "impact")),
                SoundCategory.BLOCKS, 8.0f, 0.55f);

            // A large burst so the impact reads as massive before the blocks
            // finish being removed over the coming ticks. Spread is scaled to
            // the strike radius so a small strike stays small.
            double r = tuning.radius();
            serverWorld.spawnParticles(net.minecraft.particle.ParticleTypes.EXPLOSION_EMITTER,
                x, y, z, (int) Math.min(40, Math.max(4, r / 2)), r * 0.5, 2.0, r * 0.5, 0.0);
            serverWorld.spawnParticles(net.minecraft.particle.ParticleTypes.LARGE_SMOKE,
                x, y, z, (int) Math.min(400, Math.max(30, r * 4)), r * 0.6, 4.0, r * 0.6, 0.4);
            serverWorld.spawnParticles(net.minecraft.particle.ParticleTypes.FLAME,
                x, y, z, (int) Math.min(300, Math.max(20, r * 3)), r * 0.6, 3.0, r * 0.6, 0.6);
        }
        discard();
    }

    /** Minecraft has no isFinite() on Vec3d, and NaN here would poison the world. */
    private static boolean isFinite(Vec3d v) {
        return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }

    private java.util.UUID getOwnerUuid() {
        var owner = getOwner();
        return owner != null ? owner.getUuid() : java.util.UUID.randomUUID();
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        age = nbt.getInt("age");
        if (nbt.contains("radius")) {
            tuning = new StrikeTuning(
                nbt.getDouble("radius"),
                nbt.getDouble("damage"),
                nbt.getDouble("speed"),
                nbt.getInt("lifetime"));
        }
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("age", age);
        nbt.putDouble("radius", tuning.radius());
        nbt.putDouble("damage", tuning.damage());
        nbt.putDouble("speed", tuning.speed());
        nbt.putInt("lifetime", tuning.lifetimeTicks());
    }

    @Override
    protected void onEntityHit(EntityHitResult entityHitResult) {
        // The rocket hits blocks; entity collision when it does so is
        // intentional (it is a fast, small hitbox).
        super.onEntityHit(entityHitResult);
    }
}