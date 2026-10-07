package sigf.mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import sigf.kit.Sigf;

/** The night shift: a 12 AM to 6 AM clock, the power meter, the blast door, jump scares and the flashlight beam. */
public final class Night {
	public static SoundEvent JUMPSCARE, MUSIC_BOX, PHONE_RING, CLANK, DOOR_SLAM, HONK, FLASH_CLICK, CHIME, POWER_DOWN, HIT_CLANG, HUM, FOXY_RUN, CHEER, STATIC_BURST;

	static void registerSounds() {
		JUMPSCARE = Sigf.registerSound("jumpscare");
		MUSIC_BOX = Sigf.registerSound("music_box");
		PHONE_RING = Sigf.registerSound("phone_ring");
		CLANK = Sigf.registerSound("clank");
		DOOR_SLAM = Sigf.registerSound("door_slam");
		HONK = Sigf.registerSound("honk");
		FLASH_CLICK = Sigf.registerSound("flashlight_click");
		CHIME = Sigf.registerSound("chime_6am");
		POWER_DOWN = Sigf.registerSound("power_down");
		HIT_CLANG = Sigf.registerSound("hit_clang");
		HUM = Sigf.registerSound("ambience_hum");
		FOXY_RUN = Sigf.registerSound("foxy_run");
		CHEER = Sigf.registerSound("kids_cheer");
		STATIC_BURST = Sigf.registerSound("static_burst");
	}

	static boolean active, powerOut;
	static int night = 1;
	static long t;
	/** Server ticks per in-game hour (demo: faster, so the whole night fits in the clip). */
	static int hourTicks = 900;
	static float power = 100f;
	static final Map<UUID, Long> lastScare = new HashMap<>();
	static final Map<UUID, Long> beamUntil = new HashMap<>();

	public static boolean hunting() { return active && t > 80; }
	public static double difficulty() { return (1.0 + 0.12 * (night - 1)) * (powerOut ? 1.4 : 1.0); }
	public static boolean doorBlocks(Animatronic a) { return Pizzeria.isClosed() && a.position().distanceTo(Pizzeria.doorCenter()) < 5.5; }

	public static void start(int n, int hourSeconds) {
		night = n;
		hourTicks = hourSeconds * 20;
		t = 0;
		power = 100f;
		powerOut = false;
		Pizzeria.setDoor(false);
		active = true;
		for (Animatronic a : Pizzeria.band()) a.setIdle(false);
		Sigf.title("NIGHT " + n, "12 AM", 3);
		Sigf.sound(PHONE_RING, Pizzeria.world(15, 1, 19), 1.5f, 1f);
	}

	public static void toggleDoor(Player p) {
		if (!active) return;
		boolean close = !Pizzeria.isClosed();
		if (close && powerOut) { Sigf.sound(FLASH_CLICK, p.position(), 0.8f, 0.6f); return; }
		Pizzeria.setDoor(close);
		Sigf.sound(DOOR_SLAM, Pizzeria.doorCenter(), 1.4f, close ? 1f : 1.4f);
		Sigf.particles(ParticleTypes.CRIT, Pizzeria.doorCenter(), 20, 0.8);
	}

	/** An animatronic pounds on the closed door: it costs power. */
	public static void bang(Animatronic a) {
		Sigf.sound(DOOR_SLAM, Pizzeria.doorCenter(), 1.3f, 0.7f);
		Sigf.particles(ParticleTypes.CRIT, Pizzeria.doorCenter().add(0, 0.5, -0.8), 14, 0.6);
		power = Math.max(0, power - (a.kind == Animatronic.Kind.FOXY ? 6f : 2.5f));
	}

	public static boolean canScare(Player p) { return t - lastScare.getOrDefault(p.getUUID(), -1000L) > 500; }

	public static void jumpscare(Animatronic a, Player p) {
		lastScare.put(p.getUUID(), t);
		Sigf.sound(JUMPSCARE, p.position(), 2f, 1f);
		if (p instanceof ServerPlayer sp) {
			ServerPlayNetworking.send(sp, new FnafPayload(1, a.kind.ordinal(), 0, 0, 0));
			ServerLevel level = sp.level();
			sp.hurtServer(level, level.damageSources().mobAttack(a), a.kind.damage);
			Vec3 away = p.position().subtract(a.position()).multiply(1, 0, 1).normalize().scale(1.1).add(0, 0.45, 0);
			sp.setDeltaMovement(away);
			sp.syncVelocity = true;
			sp.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1));
		}
	}

	/** Flashlight: lights a beam for 3 seconds; animatronics inside it freeze like when watched. */
	public static void useFlashlight(Player p) {
		beamUntil.put(p.getUUID(), t + 60);
		power = Math.max(0, power - 2f);
		Sigf.sound(FLASH_CLICK, p.position(), 1f, 1f);
		Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
		for (int i = 2; i < 18; i += 2) Sigf.particles(ParticleTypes.END_ROD, eye.add(look.scale(i)), 3, 0.35 + i * 0.03);
	}

	public static boolean flashlightBeam(Player p, Animatronic a) {
		if (beamUntil.getOrDefault(p.getUUID(), -1L) < t) return false;
		Vec3 to = a.position().add(0, 1, 0).subtract(p.getEyePosition());
		double d = to.length();
		return d < 20 && to.normalize().dot(p.getLookAngle()) > 0.86;
	}

	/** The plush's nose honks: every animatronic nearby freezes for 4 seconds, listening. */
	public static void honk(Player p) {
		Sigf.sound(HONK, p.position(), 1.6f, 1f);
		for (Animatronic a : Pizzeria.band()) {
			if (a.distanceTo(p) < 18) {
				a.stun(80);
				Sigf.particles(ParticleTypes.NOTE, a.position().add(0, 2.4, 0), 4, 0.3);
			}
		}
	}

	static boolean holdingFlashlight(Player p) {
		for (ItemStack s : new ItemStack[] {p.getMainHandItem(), p.getOffhandItem()}) if (s.is(SigfMod.FLASHLIGHT)) return true;
		return false;
	}

	/** Called every server tick. */
	static void tick() {
		if (Sigf.server() == null || !Pizzeria.built) return;
		if (!active) return;
		t++;
		float hour = t / (float) hourTicks;
		double speedUp = 900.0 / hourTicks;
		float drain = (float) ((0.07 + (Pizzeria.isClosed() ? 0.30 : 0.0)) * speedUp / 20.0);
		power = Math.max(0, power - drain);
		if (power <= 0 && !powerOut) {
			powerOut = true;
			Pizzeria.setDoor(false);
			Sigf.sound(POWER_DOWN, Pizzeria.world(15, 1, 19), 2f, 1f);
			Sigf.title("POWER OUT", "It is coming...", 3);
			for (ServerPlayer p : Sigf.players()) p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 400, 0));
		}
		if (t % 10 == 0) {
			int flags = (Pizzeria.isClosed() ? 1 : 0) | (powerOut ? 2 : 0);
			for (ServerPlayer p : Sigf.players()) ServerPlayNetworking.send(p, new FnafPayload(0, (int) (hour * 10), (int) Math.ceil(power), night, flags));
		}
		if (t % 100 == 0 && !powerOut) Sigf.sound(HUM, Pizzeria.world(15, 3, 12), 0.5f, 1f);
		if (hour >= 6f) win();
	}

	static void win() {
		active = false;
		Sigf.title("6 AM", "You survived night " + night, 4);
		Sigf.sound(CHIME, Pizzeria.world(15, 2, 19), 2f, 1f);
		Sigf.sound(CHEER, Pizzeria.world(15, 2, 12), 1.5f, 1f);
		Pizzeria.setDoor(false);
		for (ServerPlayer p : Sigf.players()) {
			ServerPlayNetworking.send(p, new FnafPayload(0, 60, (int) Math.ceil(power), night, 0));
			p.removeEffect(MobEffects.DARKNESS);
			Sigf.particles(ParticleTypes.TOTEM_OF_UNDYING, p.position().add(0, 1.5, 0), 60, 1.5);
		}
		for (Animatronic a : Pizzeria.band()) {
			a.setIdle(true);
			Sigf.particles(ParticleTypes.FIREWORK, a.position().add(0, 1.2, 0), 40, 0.8);
			Sigf.particles(ParticleTypes.HAPPY_VILLAGER, a.position().add(0, 2, 0), 12, 0.6);
			if (a.home() != null) Sigf.teleport(a, a.home(), Vec3.ZERO);
		}
		if (!Sigf.isDemo()) {
			int next = night + 1;
			Sigf.after(12, () -> { Sigf.title("NIGHT " + next, "They are faster...", 3); Sigf.after(3, () -> start(next, 45)); });
		}
	}
}
