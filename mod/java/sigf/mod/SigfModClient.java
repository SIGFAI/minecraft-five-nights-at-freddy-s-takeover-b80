package sigf.mod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import sigf.kit.Sigf;

/** Client side: animatronic renderers, the night clock and power meter, and the jump scare screen. */
public final class SigfModClient implements ClientModInitializer {
	static int hourX10, power = 100, night = 1, flags;
	static boolean clockOn;
	static int jumpKind = -1;
	static long jumpStart;
	static final long JUMP_MS = 1500;

	@Override
	public void onInitializeClient() {
		for (Animatronic.Kind k : Animatronic.Kind.values()) {
			ModelLayerRegistry.registerModelLayer(AnimatronicRenderer.layer(k), () -> AnimatronicModel.layer(k));
			EntityRendererRegistry.register(SigfMod.type(k), ctx -> new AnimatronicRenderer(ctx, k));
		}
		ClientPlayNetworking.registerGlobalReceiver(FnafPayload.TYPE, (p, ctx) -> {
			if (p.mode() == 0) {
				clockOn = true;
				hourX10 = p.a(); power = p.b(); night = p.c(); flags = p.d();
			} else {
				jumpKind = p.a();
				jumpStart = System.currentTimeMillis();
			}
		});
		HudElementRegistry.addLast(Sigf.id("night_hud"), SigfModClient::drawHud);
		HudElementRegistry.addLast(Sigf.id("jumpscare"), SigfModClient::drawJumpscare);
	}

	static String hourLabel() {
		int h = Math.min(6, hourX10 / 10);
		return (h == 0 ? 12 : h) + " AM";
	}

	private static void big(GuiGraphicsExtractor g, Font f, String s, int x, int y, float scale, int color) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.text(f, s, 0, 0, color, true);
		g.pose().popMatrix();
	}

	private static void drawHud(GuiGraphicsExtractor g, net.minecraft.client.DeltaTracker dt) {
		Minecraft mc = Minecraft.getInstance();
		if (!clockOn || mc.player == null || mc.player.isSpectator()) return;
		Font f = mc.font;
		int w = g.guiWidth(), h = g.guiHeight();
		// Clock, top right.
		String clock = hourLabel();
		g.fill(w - 104, 6, w - 6, 50, 0xAA000000);
		g.fill(w - 104, 6, w - 6, 8, 0xFF9A1B1B);
		big(g, f, clock, w - 98, 13, 2.4f, 0xFFFFFFFF);
		g.text(f, "Night " + night, w - 98, 38, 0xFFB8B8B8, true);
		// Power, bottom left.
		boolean out = (flags & 2) != 0;
		int pcol = out || power <= 15 ? 0xFFFF4040 : power <= 40 ? 0xFFFFD040 : 0xFFFFFFFF;
		g.fill(8, h - 60, 150, h - 30, 0xAA000000);
		g.text(f, out ? "POWER OUT" : "Power left: " + power + "%", 14, h - 56, pcol, true);
		int bars = 1 + ((flags & 1) != 0 ? 2 : 0);
		g.text(f, "Usage:", 14, h - 42, 0xFFB8B8B8, true);
		for (int i = 0; i < 4; i++) {
			int c = i < bars ? (i < 2 ? 0xFF50E050 : i == 2 ? 0xFFFFD040 : 0xFFFF4040) : 0xFF333333;
			g.fill(56 + i * 14, h - 43, 66 + i * 14, h - 33, c);
		}
		if ((flags & 1) != 0) g.text(f, "DOOR CLOSED", w / 2 - 36, h - 52, 0xFFFF6060, true);
	}

	private static void drawJumpscare(GuiGraphicsExtractor g, net.minecraft.client.DeltaTracker dt) {
		if (jumpKind < 0) return;
		long el = System.currentTimeMillis() - jumpStart;
		if (el > JUMP_MS) { jumpKind = -1; return; }
		int w = g.guiWidth(), h = g.guiHeight();
		Identifier tex = Sigf.id("textures/gui/jump_" + Animatronic.Kind.values()[jumpKind].id + ".png");
		// Violent shake and zoom, flickering to black like a glitching camera.
		float k = el / (float) JUMP_MS;
		int sx = (int) (Math.sin(el * 0.9) * 14 * (1 - k)), sy = (int) (Math.cos(el * 1.3) * 10 * (1 - k));
		int grow = (int) (60 * k);
		boolean flicker = el < 1100 && (el / 60) % 5 == 4;
		g.fill(0, 0, w, h, 0xFF000000);
		if (!flicker) g.blit(RenderPipelines.GUI_TEXTURED, tex, -grow + sx, -grow + sy, 0, 0, w + grow * 2, h + grow * 2, 512, 288, 512, 288);
		if (el < 150) g.fill(0, 0, w, h, 0x88FF0000);
		if (el > 1000) g.fill(0, 0, w, h, (int) ((el - 1000) / 500f * 255) << 24);
	}
}
