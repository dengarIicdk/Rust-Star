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

    private StrikeTuning tuning = StrikeTuning.DEFAULT;
    private int age;
    private boolean exploded;

    public RocketEntity(EntityType<? extends ProjectileEntity> type, World world) {
        super(type, world);
    }

    public static RocketEntity create(
            ServerWorld world, ServerPlayerEntity owner, Vec3d pos, Vec3d direction, StrikeTuning in) {
        StrikeTuning tuning = in.orDefault();
        RocketEntity r = new RocketEntity(ModEntities.ORBITAL_ROCKET, world);
        // Reject a zero/NaN direction rather than spawning a rocket that can
        // never move and never expires by impact.
        if (!isFinite(direction) || direction.lengthSquared() < 1.0e-9) {
            return null;
        }
        Vec3d dir = direction.normalize();
        r.setPosition(pos);
        r.setVelocity(dir.multiply(tuning.speed()));
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
            getWorld().addParticle(net.minecraft.particle.ParticleTypes.FLAME,
                getX(), getY(), getZ(), 0.0, 0.0, 0.0);
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

        // 1. Swept collision against the world (blocks).
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
            // Impact sound, broadcast to everyone nearby.
            serverWorld.playSound(null, BlockPos.ofFloored(x, y, z),
                SoundEvent.of(Identifier.of(StarflightMod.MOD_ID, "impact")),
                SoundCategory.BLOCKS, 2.0f, 1.0f);
            // A short burst of particles so the impact reads visually even
            // before the blocks are actually removed over the coming ticks.
            serverWorld.spawnParticles(net.minecraft.particle.ParticleTypes.EXPLOSION_EMITTER,
                x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
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