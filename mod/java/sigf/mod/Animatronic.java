package sigf.mod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import sigf.kit.Sigf;

/**
 * A haunted pizzeria mascot. It freezes the moment a player looks at it and creeps forward whenever nobody watches.
 * Four kinds share this class, each with its own skin, speed and trick.
 */
public final class Animatronic extends Monster {
	public enum Kind {
		FREDDY("freddy", 44, 0.23, 8f, 1.5),
		BONNIE("bonnie", 32, 0.25, 6f, 1.4),
		CHICA("chica", 32, 0.25, 6f, 1.35),
		FOXY("foxy", 26, 0.30, 7f, 1.35);

		public final String id; final double health, speed, scale; final float damage;
		Kind(String id, double health, double speed, float damage, double scale) { this.id = id; this.health = health; this.speed = speed; this.damage = damage; this.scale = scale; }

		AttributeSupplier.Builder attributes() {
			return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, health).add(Attributes.MOVEMENT_SPEED, speed)
				.add(Attributes.SCALE, scale).add(Attributes.FOLLOW_RANGE, 60.0).add(Attributes.ATTACK_DAMAGE, damage).add(Attributes.KNOCKBACK_RESISTANCE, 0.3);
		}
	}

	private static final EntityDataAccessor<Boolean> SCARING = SynchedEntityData.defineId(Animatronic.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> FROZEN = SynchedEntityData.defineId(Animatronic.class, EntityDataSerializers.BOOLEAN);

	public final Kind kind;
	private int stun, scareTicks, unobserved, sinceHop, bangCooldown;
	private boolean idle = true;
	private Vec3 home;

	public Animatronic(EntityType<? extends Animatronic> type, Level level, Kind kind) {
		super(type, level);
		this.kind = kind;
		this.xpReward = 10;
		this.setPersistenceRequired();
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder data) {
		super.defineSynchedData(data);
		data.define(SCARING, false);
		data.define(FROZEN, false);
	}

	public boolean isScaring() { return this.entityData.get(SCARING); }
	public boolean isFrozenByGaze() { return this.entityData.get(FROZEN); }
	@Override public boolean removeWhenFarAway(double dist) { return false; }

	/** Where the animatronic returns when the night ends. */
	public void setHome(Vec3 pos) { this.home = pos; }
	public Vec3 home() { return home; }
	public void stun(int ticks) { stun = Math.max(stun, ticks); }
	public void setIdle(boolean idle) { this.idle = idle; }

	/** A frozen, stunned or idle animatronic cannot move (the AI skips its movement). */
	@Override
	protected boolean isImmobile() { return super.isImmobile() || idle || stun > 0 || isFrozenByGaze(); }

	@Override
	public void tick() {
		super.tick();
		if (level() instanceof ServerLevel sl && isAlive()) think(sl);
	}

	private boolean watchedBy(Player p) {
		if (distanceTo(p) > 45) return false;
		return this.isLookingAtMe(p, 0.38, false, false, getEyeY(), getY() + 0.7);
	}

	private void think(ServerLevel level) {
		if (stun > 0) stun--;
		if (bangCooldown > 0) bangCooldown--;
		if (scareTicks > 0 && --scareTicks == 0) this.entityData.set(SCARING, false);
		Player target = level.getNearestPlayer(this, 70);
		boolean hunting = Night.hunting() && target != null && !idle && (kind != Kind.FOXY || Night.t > 140);
		if (target != null && (idle || stun > 0 || isFrozenByGaze())) this.getLookControl().setLookAt(target, 40f, 40f);
		if (!hunting) { this.entityData.set(FROZEN, false); return; }

		boolean watched = watchedBy(target) || Night.flashlightBeam(target, this);
		if (watched) {
			if (!isFrozenByGaze() && tickCount % 4 == 0) {
				Sigf.particles(ParticleTypes.ELECTRIC_SPARK, position().add(0, 1.6, 0), 3, 0.3);
			}
			unobserved = 0;
			this.entityData.set(FROZEN, true);
			getNavigation().stop();
			return;
		}
		this.entityData.set(FROZEN, false);
		unobserved++;
		double d = distanceTo(target);
		double boost = Night.difficulty();
		// Foxy sprints after a few seconds out of sight, the others walk and blink forward in the dark.
		double speed = switch (kind) {
			case FOXY -> unobserved > 30 ? 1.9 : 0.9;
			case FREDDY -> 0.75;
			default -> 0.9;
		} * boost;
		if (tickCount % 8 == 0) getNavigation().moveTo(target, speed);
		if (kind == Kind.FOXY && unobserved == 30) Sigf.sound(Night.FOXY_RUN, position(), 1.3f, 1f);
		if (kind == Kind.FREDDY && tickCount % 70 == 0 && d < 28) Sigf.sound(Night.MUSIC_BOX, position(), 1.2f, 1f);
		if (kind == Kind.BONNIE || kind == Kind.CHICA) {
			sinceHop++;
			if (unobserved > 50 && sinceHop > (int) (70 / boost) && d > 6) hop(level, target);
		}
		if (d < 4 && Night.doorBlocks(this) && bangCooldown == 0) {
			bangCooldown = 40;
			Night.bang(this);
		}
		if (d < 1.9 && Math.abs(target.getY() - getY()) < 2.2 && Night.canScare(target)) scare(target);
	}

	/** Blinks a few blocks toward the target, in a burst of static, when nobody is looking. */
	private void hop(ServerLevel level, Player target) {
		sinceHop = 0;
		Vec3 dir = target.position().subtract(position()).multiply(1, 0, 1).normalize();
		for (double step = 4.5; step >= 2; step -= 1) {
			Vec3 to = position().add(dir.scale(step));
			BlockPos bp = BlockPos.containing(to);
			BlockState below = level.getBlockState(bp.below());
			if (below.isSolidRender() || !below.getCollisionShape(level, bp.below()).isEmpty()) {
				if (level.noCollision(this, getBoundingBox().move(to.subtract(position())))) {
					Sigf.particles(ParticleTypes.ELECTRIC_SPARK, position().add(0, 1, 0), 12, 0.4);
					Sigf.sound(Night.STATIC_BURST, position(), 0.6f, 1.2f);
					Sigf.teleport(this, to, Vec3.ZERO);
					Sigf.particles(ParticleTypes.ELECTRIC_SPARK, to.add(0, 1, 0), 12, 0.4);
					return;
				}
			}
		}
	}

	private void scare(Player target) {
		this.entityData.set(SCARING, true);
		scareTicks = 34;
		stun = 50;
		getNavigation().stop();
		Night.jumpscare(this, target);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt) {
			Sigf.particles(ParticleTypes.ELECTRIC_SPARK, position().add(0, 1.3, 0), 18, 0.45);
			Sigf.particles(ParticleTypes.CRIT, position().add(0, 1.3, 0), 6, 0.3);
			stun = Math.max(stun, 8);
		}
		return hurt;
	}

	@Override protected SoundEvent getHurtSound(DamageSource source) { return Night.HIT_CLANG; }
	@Override protected SoundEvent getDeathSound() { return Night.POWER_DOWN; }
	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		if (!isFrozenByGaze()) this.playSound(Night.CLANK, 0.35f, 0.9f + random.nextFloat() * 0.2f);
	}
}
