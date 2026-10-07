package sigf.mod;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import sigf.kit.Sigf;

/**
 * Freddy Fazbear's Pizza, built from vanilla blocks around the spawn point:
 * checkered dining hall, show stage with a glowing sign, Pirate Cove, arcade corner and the security office with its blast door.
 * Local coordinates: x east, z south, y 0 = the floor the player stands on.
 */
public final class Pizzeria {
	public static final int W = 30, D = 22, H = 9;
	static ServerLevel L;
	static int ox, fy, oz;
	static boolean built;
	static final List<BlockPos> buttons = new ArrayList<>();
	static final int DOOR_Z = 17, DOOR_X0 = 13, DOOR_X1 = 17;

	static BlockPos at(int x, int y, int z) { return new BlockPos(ox + x, fy + y, oz + z); }
	public static Vec3 world(double x, double y, double z) { return new Vec3(ox + x, fy + y, oz + z); }
	static void set(int x, int y, int z, BlockState s) { L.setBlock(at(x, y, z), s, 18); }
	static void set(int x, int y, int z, Block b) { set(x, y, z, b.defaultBlockState()); }
	static void fill(int x1, int y1, int z1, int x2, int y2, int z2, Block b) {
		for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
			for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
				for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) set(x, y, z, b);
	}
	static void light(int x, int y, int z, int level) { set(x, y, z, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, level)); }
	static void slab(int x, int y, int z, Block b, SlabType t) { set(x, y, z, b.defaultBlockState().setValue(SlabBlock.TYPE, t)); }
	static void stair(int x, int y, int z, Block b, Direction f) { set(x, y, z, b.defaultBlockState().setValue(StairBlock.FACING, f)); }
	static void cmd(int x, int y, int z, String state) { BlockPos p = at(x, y, z); Sigf.command("setblock " + p.getX() + " " + p.getY() + " " + p.getZ() + " " + state); }

	/** Builds the whole place; the office (15, 19) lands on the given spawn position. */
	public static void build(Vec3 spawn) {
		L = Sigf.level();
		ox = (int) Math.floor(spawn.x) - 15;
		oz = (int) Math.floor(spawn.z) - 19;
		fy = (int) Math.floor(spawn.y);

		// Clear the volume and give it a solid foundation.
		for (int x = -2; x <= W + 2; x++)
			for (int z = -2; z <= D + 2; z++) {
				for (int y = 0; y <= H + 8; y++) set(x, y, z, Blocks.AIR);
				for (int y = -1; y >= -9; y--) {
					BlockPos p = at(x, y, z);
					BlockState s = L.getBlockState(p);
					if (s.isAir() || !s.getFluidState().isEmpty() || s.canBeReplaced()) set(x, y, z, Blocks.STONE_BRICKS);
					else break;
				}
			}
		// Floor: checkered tiles, the office is plain.
		for (int x = 0; x <= W; x++)
			for (int z = 0; z <= D; z++) {
				boolean office = z >= DOOR_Z && x >= 10 && x <= 20;
				set(x, -1, z, office ? Blocks.CONCRETE.pick(DyeColor.GRAY) : ((x + z) & 1) == 0 ? Blocks.CONCRETE.pick(DyeColor.WHITE) : Blocks.CONCRETE.pick(DyeColor.BLACK));
			}
		// Outer walls and ceiling: purple, a yellow stripe, light blue on top.
		for (int y = 0; y < H; y++) {
			Block wall = y <= 2 ? Blocks.CONCRETE.pick(DyeColor.PURPLE) : y == 3 ? Blocks.CONCRETE.pick(DyeColor.YELLOW) : Blocks.CONCRETE.pick(DyeColor.LIGHT_BLUE);
			for (int x = 0; x <= W; x++) { set(x, y, 0, wall); set(x, y, D, wall); }
			for (int z = 0; z <= D; z++) { set(0, y, z, wall); set(W, y, z, wall); }
		}
		fill(0, H, 0, W, H, D, Blocks.CONCRETE.pick(DyeColor.BLACK));
		fill(0, H + 1, 0, W, H + 1, D, Blocks.CONCRETE.pick(DyeColor.GRAY));
		// Entrance on the east wall.
		fill(W, 0, 12, W, 2, 14, Blocks.AIR);
		fill(W, 3, 12, W, 3, 14, Blocks.GLOWSTONE);

		stage();
		cove();
		tables();
		arcade();
		office();
		lights();
		roofSign();
		built = true;
	}

	private static final String[][] FONT = {
		{"111", "100", "110", "100", "100"}, // F
		{"110", "101", "110", "101", "101"}, // R
		{"111", "100", "110", "100", "111"}, // E
		{"110", "101", "101", "101", "110"}, // D
		{"101", "101", "010", "010", "010"}, // Y
		{"1", "1", "0", "0", "0"},           // '
		{"111", "100", "111", "001", "111"}, // S
	};

	/** Draws FREDDY'S as a glowstone sign, 3x5 pixels per letter, standing on its bottom row y0, x from x0. */
	private static void letters(int x0, int y0, int z, boolean flat) {
		int[] order = {0, 1, 2, 3, 3, 4, 5, 6};
		int x = x0;
		for (int idx : order) {
			String[] g = FONT[idx];
			for (int r = 0; r < 5; r++)
				for (int c = 0; c < g[r].length(); c++)
					if (g[r].charAt(c) == '1') set(x + c, y0 + 4 - r, z, Blocks.GLOWSTONE);
			x += g[0].length() + 1;
		}
	}

	private static void stage() {
		// Wall sign behind the stage.
		fill(1, 4, 1, 29, 8, 1, Blocks.CONCRETE.pick(DyeColor.BLACK));
		letters(1, 4, 1, false);
		// Stage floor (half blocks: everything can walk up), curtains and a valance.
		for (int x = 8; x <= 22; x++) for (int z = 1; z <= 6; z++) slab(x, 0, z, Blocks.DARK_OAK_SLAB, SlabType.BOTTOM);
		for (int y = 0; y <= 3; y++) {
			for (int x : new int[] {8, 9, 21, 22}) set(x, y, 1, Blocks.WOOL.pick(DyeColor.RED));
		}
		fill(8, 3, 1, 22, 3, 1, Blocks.WOOL.pick(DyeColor.RED));
		// Star lights on the stage rim.
		for (int x = 8; x <= 22; x += 2) set(x, 0, 7, Blocks.CARPET.pick(DyeColor.YELLOW));
	}

	private static void cove() {
		fill(25, -1, 1, 29, -1, 6, Blocks.DARK_OAK_PLANKS);
		for (int y = 0; y <= 3; y++) for (int x : new int[] {25, 26, 28, 29}) set(x, y, 7, Blocks.WOOL.pick(DyeColor.PURPLE));
		fill(25, 4, 7, 29, 4, 7, Blocks.WOOL.pick(DyeColor.PURPLE));
		set(27, 3, 7, Blocks.WOOL.pick(DyeColor.PURPLE));
		set(24, 0, 1, Blocks.BARREL); set(24, 0, 2, Blocks.BARREL);
		set(29, 0, 1, Blocks.BARREL); set(25, 0, 1, Blocks.CHEST);
		fill(25, 1, 0, 29, 1, 0, Blocks.DARK_OAK_PLANKS);
	}

	private static void tables() {
		int[][] spots = {{3, 10}, {3, 15}, {24, 10}, {24, 15}, {6, 12}, {21, 12}};
		Block[] candles = {Blocks.DYED_CANDLE.pick(DyeColor.RED), Blocks.DYED_CANDLE.pick(DyeColor.YELLOW), Blocks.DYED_CANDLE.pick(DyeColor.BLUE), Blocks.DYED_CANDLE.pick(DyeColor.PINK), Blocks.DYED_CANDLE.pick(DyeColor.LIME), Blocks.DYED_CANDLE.pick(DyeColor.PURPLE)};
		int i = 0;
		for (int[] s : spots) {
			int x = s[0], z = s[1];
			for (int dx = 0; dx < 3; dx++) for (int dz = 0; dz < 2; dz++) slab(x + dx, 0, z + dz, Blocks.SMOOTH_QUARTZ_SLAB, SlabType.TOP);
			set(x + 1, 0, z, Blocks.AIR); slab(x + 1, 0, z, Blocks.SMOOTH_QUARTZ_SLAB, SlabType.TOP);
			set(x + 1, 1, z, i % 2 == 0 ? Blocks.CAKE : candles[i % candles.length]);
			set(x, 1, z + 1, candles[(i + 1) % candles.length]);
			set(x + 2, 1, z + 1, candles[(i + 2) % candles.length]);
			for (int dx = 0; dx < 3; dx += 2) {
				stair(x + dx, 0, z - 1, Blocks.DARK_OAK_STAIRS, Direction.SOUTH);
				stair(x + dx, 0, z + 2, Blocks.DARK_OAK_STAIRS, Direction.NORTH);
			}
			i++;
		}
		// Party streamers under the ceiling.
		Block[] colors = {Blocks.STAINED_GLASS.pick(DyeColor.RED), Blocks.STAINED_GLASS.pick(DyeColor.YELLOW), Blocks.STAINED_GLASS.pick(DyeColor.BLUE), Blocks.STAINED_GLASS.pick(DyeColor.LIME), Blocks.STAINED_GLASS.pick(DyeColor.PINK)};
		for (int x = 2; x <= 28; x++) set(x, H - 1, 12, colors[x % colors.length]);
		for (int x = 2; x <= 28; x++) if (x % 3 == 0) set(x, H - 2, 12, colors[(x + 2) % colors.length]);
	}

	private static void arcade() {
		for (int z : new int[] {9, 12, 15, 18}) {
			set(1, 0, z, Blocks.CONCRETE.pick(DyeColor.BLACK));
			set(1, 1, z, Blocks.SEA_LANTERN);
			set(1, 2, z, Blocks.CONCRETE.pick(DyeColor.BLACK));
			set(2, 0, z, Blocks.CARPET.pick(DyeColor.GRAY));
			cmd(2, 1, z, "stone_button[face=floor,facing=east]");
		}
		for (int z : new int[] {9, 12, 15, 18}) set(1, 3, z, Blocks.CONCRETE.pick(DyeColor.RED));
	}

	private static void office() {
		// Walls: front wall at z=DOOR_Z with the blast door opening, side walls.
		for (int y = 0; y < H; y++) {
			for (int x = 10; x <= 20; x++) set(x, y, DOOR_Z, Blocks.CONCRETE.pick(DyeColor.GRAY));
			for (int z = DOOR_Z; z <= D; z++) { set(10, y, z, Blocks.CONCRETE.pick(DyeColor.GRAY)); set(20, y, z, Blocks.CONCRETE.pick(DyeColor.GRAY)); }
		}
		fill(DOOR_X0, 0, DOOR_Z, DOOR_X1, 3, DOOR_Z, Blocks.AIR);
		fill(DOOR_X0 - 1, 0, DOOR_Z, DOOR_X0 - 1, 3, DOOR_Z, Blocks.POLISHED_BLACKSTONE);
		fill(DOOR_X1 + 1, 0, DOOR_Z, DOOR_X1 + 1, 3, DOOR_Z, Blocks.POLISHED_BLACKSTONE);
		fill(DOOR_X0 - 1, 4, DOOR_Z, DOOR_X1 + 1, 4, DOOR_Z, Blocks.POLISHED_BLACKSTONE);
		// Desk with glowing monitors and a door button on each side.
		fill(12, 0, 21, 18, 0, 21, Blocks.DARK_OAK_PLANKS);
		fill(12, 1, 21, 18, 1, 21, Blocks.DARK_OAK_PLANKS);
		for (int x : new int[] {13, 15, 17}) { set(x, 2, 21, Blocks.SEA_LANTERN); set(x, 1, 20, Blocks.STAINED_GLASS_PANE.pick(DyeColor.LIGHT_BLUE)); }
		cmd(12, 1, 18, "stone_button[face=wall,facing=south]");
		cmd(18, 1, 18, "stone_button[face=wall,facing=south]");
		buttons.clear();
		buttons.add(at(12, 1, 18));
		buttons.add(at(18, 1, 18));
		setDoor(false);
		cmd(15, 8, 19, "soul_lantern[hanging=true]");
	}

	private static boolean closed;
	/** Raises or lowers the blast door and flips the indicator lights. */
	public static void setDoor(boolean close) {
		closed = close;
		fill(DOOR_X0, 0, DOOR_Z, DOOR_X1, 3, DOOR_Z, close ? Blocks.IRON_BARS : Blocks.AIR);
		for (int x : new int[] {12, 18}) set(x, 2, 18, close ? Blocks.CONCRETE.pick(DyeColor.RED) : Blocks.CONCRETE.pick(DyeColor.LIME));
	}
	public static boolean isClosed() { return closed; }
	public static Vec3 doorCenter() { return world(15.5, 1.5, DOOR_Z + 0.5); }
	public static boolean isButton(BlockPos p) { return buttons.contains(p); }

	private static void lights() {
		// Soul lanterns hang on a grid: dim, blue, creepy, but you can still see.
		for (int x = 3; x <= 27; x += 6)
			for (int z : new int[] {4, 11, 17}) {
				if (z == 17 && (x >= 9 && x <= 21)) continue;
				cmd(x, H - 1, z, "soul_lantern[hanging=true]");
			}
		// Colored stage lights.
		Block[] cols = {Blocks.OCHRE_FROGLIGHT, Blocks.PEARLESCENT_FROGLIGHT, Blocks.VERDANT_FROGLIGHT};
		int i = 0;
		for (int x = 9; x <= 21; x += 3) set(x, H - 1, 3, cols[i++ % 3]);
		// Invisible fill light so nothing spawns in the dark corners.
		for (int x = 2; x <= 28; x += 4) for (int z = 2; z <= 14; z += 4) light(x, H - 2, z, 12);
		light(15, 2, 20, 9);
		set(27, 3, 4, Blocks.STAINED_GLASS.pick(DyeColor.ORANGE));
		// Entrance doorway glow.
		light(28, 1, 13, 10);
	}

	private static void roofSign() {
		fill(0, H + 2, 1, W, H + 7, 1, Blocks.CONCRETE.pick(DyeColor.BLACK));
		letters(1, H + 3, 1, false);
	}

	/** Spawns the band on the stage and Foxy in his cove. */
	public static List<Animatronic> spawnBand() {
		List<Animatronic> out = new ArrayList<>();
		out.add(place(SigfMod.FREDDY, world(15.5, 0.5, 3.5)));
		out.add(place(SigfMod.BONNIE, world(11.5, 0.5, 3.5)));
		out.add(place(SigfMod.CHICA, world(19.5, 0.5, 3.5)));
		out.add(place(SigfMod.FOXY, world(27.5, 0, 3.5)));
		return out;
	}

	private static Animatronic place(EntityType<Animatronic> type, Vec3 pos) {
		Animatronic a = type.create(L, EntitySpawnReason.COMMAND);
		a.snapTo(pos.x, pos.y, pos.z, 0f, 0f);
		a.setYHeadRot(0f);
		a.setHome(pos);
		L.addFreshEntity(a);
		return a;
	}

	public static List<Animatronic> band() {
		return L == null ? List.of() : L.getEntitiesOfClass(Animatronic.class, new AABB(Vec3.atCenterOf(at(-4, -4, -4)), Vec3.atCenterOf(at(W + 4, 14, D + 4))));
	}
}
