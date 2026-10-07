package sigf.mod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import sigf.kit.Sigf;

/** Five Nights at Freddy's Takeover: a haunted pizzeria appears around the player and its mascots hunt whoever looks away. */
public final class SigfMod implements ModInitializer {
	public static EntityType<Animatronic> FREDDY, BONNIE, CHICA, FOXY;
	public static Item FLASHLIGHT, PIZZA_SLICE, FAZ_TOKEN, FREDDY_PLUSH;

	public static EntityType<Animatronic> type(Animatronic.Kind k) {
		return switch (k) { case FREDDY -> FREDDY; case BONNIE -> BONNIE; case CHICA -> CHICA; case FOXY -> FOXY; };
	}

	private static EntityType<Animatronic> register(Animatronic.Kind kind) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Sigf.id(kind.id));
		EntityType<Animatronic> t = EntityType.Builder.<Animatronic>of((ty, level) -> new Animatronic(ty, level, kind), MobCategory.MONSTER)
			.sized(0.9f, 2.2f).eyeHeight(1.95f).clientTrackingRange(10).build(key);
		Registry.register(BuiltInRegistries.ENTITY_TYPE, key, t);
		FabricDefaultAttributeRegistry.register(t, kind.attributes());
		return t;
	}

	@Override
	public void onInitialize() {
		Night.registerSounds();
		PayloadTypeRegistry.clientboundPlay().register(FnafPayload.TYPE, FnafPayload.CODEC);
		FREDDY = register(Animatronic.Kind.FREDDY);
		BONNIE = register(Animatronic.Kind.BONNIE);
		CHICA = register(Animatronic.Kind.CHICA);
		FOXY = register(Animatronic.Kind.FOXY);

		FLASHLIGHT = Sigf.item("flashlight", p -> new Item(p.stacksTo(1)) {
			@Override
			public InteractionResult use(Level level, Player player, InteractionHand hand) {
				if (!level.isClientSide()) {
					Night.useFlashlight(player);
					player.getCooldowns().addCooldown(player.getItemInHand(hand), 70);
				}
				return InteractionResult.SUCCESS;
			}
		});
		PIZZA_SLICE = Sigf.item("pizza_slice", p -> new Item(p.food(new FoodProperties(7, 0.9f, false))));
		FAZ_TOKEN = Sigf.item("faz_token", p -> new Item(p.stacksTo(64)));
		FREDDY_PLUSH = Sigf.item("freddy_plush", p -> new Item(p.stacksTo(1)) {
			@Override
			public InteractionResult use(Level level, Player player, InteractionHand hand) {
				if (!level.isClientSide()) {
					Night.honk(player);
					player.getCooldowns().addCooldown(player.getItemInHand(hand), 120);
				}
				return InteractionResult.SUCCESS;
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> Night.tick());
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!level.isClientSide() && Pizzeria.isButton(hit.getBlockPos())) Night.toggleDoor(player);
			return InteractionResult.PASS;
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Animatronic a && a.level() instanceof ServerLevel level) {
				Vec3 p = a.position().add(0, 1, 0);
				Sigf.particles(ParticleTypes.POOF, p, 25, 0.6);
				Sigf.particles(ParticleTypes.ELECTRIC_SPARK, p, 40, 0.7);
				Sigf.particles(ParticleTypes.FIREWORK, p, 20, 0.5);
				int tokens = 2 + level.getRandom().nextInt(3);
				level.addFreshEntity(new ItemEntity(level, p.x, p.y, p.z, new ItemStack(FAZ_TOKEN, tokens)));
				level.addFreshEntity(new ItemEntity(level, p.x, p.y, p.z, new ItemStack(Items.IRON_NUGGET, 4 + level.getRandom().nextInt(5))));
				if (level.getRandom().nextFloat() < 0.5f) level.addFreshEntity(new ItemEntity(level, p.x, p.y, p.z, new ItemStack(PIZZA_SLICE, 1)));
			}
		});

		// The pizzeria rises around the player a second after they arrive.
		Sigf.after(1, () -> {
			Sigf.command("gamerule spawn_monsters false");
			Pizzeria.build(Sigf.host().position());
			Pizzeria.spawnBand();
			ServerPlayer p = Sigf.host();
			Sigf.lookAt(p, Pizzeria.world(15.5, 1.6, 3.5));
		});
		// Normal play: the night begins once a real player (not the spectating camera) is around.
		if (!Sigf.isDemo()) {
			Sigf.every(2, () -> {
				ServerPlayer p = Sigf.host();
				if (Pizzeria.built && !Night.active && Night.t == 0 && p != null && !p.isSpectator()) Night.start(1, 45);
			});
		}
		demo();
	}

	private static Animatronic nearest() {
		Animatronic best = null;
		for (Animatronic a : Pizzeria.band())
			if (a.isAlive() && (best == null || a.distanceTo(Sigf.host()) < best.distanceTo(Sigf.host()))) best = a;
		return best;
	}

	private static Animatronic find(Animatronic.Kind k) {
		for (Animatronic a : Pizzeria.band()) if (a.kind == k && a.isAlive()) return a;
		return null;
	}

	/** The demo player swings the sword at an animatronic, walking up to it first when it is far. */
	private static void strike(Animatronic a) {
		ServerPlayer p = Sigf.host();
		if (a == null || !a.isAlive()) return;
		Vec3 flat = a.position().subtract(p.position()).multiply(1, 0, 1);
		if (flat.length() > 4.2 || flat.length() < 2.6) Sigf.teleport(p, a.position().subtract(flat.normalize().scale(3.4)), Vec3.ZERO);
		Sigf.lookAt(p, a.position().add(0, 2.3, 0));
		p.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		p.resetAttackStrengthTicker();
		p.attack(a);
	}

	private void demo() {
		// 0 s: the band on stage, the clock at 12 AM.
		Sigf.demo(0.3, () -> {
			ServerPlayer p = Sigf.host();
			p.getInventory().add(new ItemStack(Items.IRON_SWORD));
			p.getInventory().add(new ItemStack(FLASHLIGHT));
			p.getInventory().add(new ItemStack(FREDDY_PLUSH));
			Sigf.teleport(p, Pizzeria.world(15.5, 0, 15.0), Vec3.ZERO);
			Sigf.lookAt(p, Pizzeria.world(15.5, 2.2, 3.5));
			Sigf.title("FIVE NIGHTS AT FREDDY'S", "Minecraft takeover", 3);
			Night.start(1, 7);
		});
		Sigf.demo(1.2, () -> Sigf.lookAt(Sigf.host(), Pizzeria.world(15.5, 2.2, 3.5)));
		// 3 s: look away: they creep forward. 6 s: look back: caught mid-step, frozen.
		Sigf.demo(4.5, () -> Sigf.lookAt(Sigf.host(), Pizzeria.world(1.5, 1.5, 12)));
		Sigf.demo(7.5, () -> {
			Sigf.lookAt(Sigf.host(), Pizzeria.world(15.5, 2.0, 4));
			Sigf.title("", "They only move when you look away", 3);
		});
		// 10 s: look away again, Bonnie blinks in right behind the player.
		Sigf.demo(10, () -> Sigf.lookAt(Sigf.host(), Pizzeria.world(1.5, 1.5, 12)));
		// 14 s: turn around, sword out: hit the frozen animatronics.
		Sigf.demo(14, () -> Sigf.lookAt(Sigf.host(), Pizzeria.world(15.5, 2.0, 4)));
		for (int i = 0; i < 6; i++) {
			Sigf.demo(15 + i * 0.85, () -> strike(find(Animatronic.Kind.FOXY)));
			Sigf.demo(21 + i * 0.85, () -> strike(find(Animatronic.Kind.BONNIE)));
		}
		// 28 s: into the office, slam the blast door.
		Sigf.demo(28, () -> {
			ServerPlayer p = Sigf.host();
			Sigf.teleport(p, Pizzeria.world(15.5, 0, 19.5), Vec3.ZERO);
			Sigf.lookAt(p, Pizzeria.world(15.5, 1.6, 8));
		});
		Sigf.demo(29.5, () -> Night.toggleDoor(Sigf.host()));
		Sigf.demo(36, () -> Night.toggleDoor(Sigf.host()));
		// 46 s: night 2. Flashlight beam freezes them, the plush honk stuns them, the sword finishes Freddy.
		Sigf.demo(46, () -> {
			ServerPlayer p = Sigf.host();
			Sigf.teleport(p, Pizzeria.world(15.5, 0, 15.0), Vec3.ZERO);
			Sigf.lookAt(p, Pizzeria.world(15.5, 2.2, 3.5));
			Night.start(2, 6);
		});
		Sigf.demo(48.5, () -> Sigf.lookAt(Sigf.host(), Pizzeria.world(1.5, 1.5, 12)));
		Sigf.demo(52, () -> { Sigf.lookAt(Sigf.host(), Pizzeria.world(15.5, 2.0, 5)); Night.useFlashlight(Sigf.host()); Sigf.title("", "The flashlight freezes them too", 3); });
		Sigf.demo(58, () -> { Night.honk(Sigf.host()); Sigf.title("", "Honk! The plush stuns them", 3); });
		for (int i = 0; i < 8; i++) Sigf.demo(60 + i * 0.85, () -> strike(nearest()));
	}
}
