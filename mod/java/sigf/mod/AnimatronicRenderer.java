package sigf.mod;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.Identifier;
import sigf.kit.Sigf;

public final class AnimatronicRenderer extends HumanoidMobRenderer<Animatronic, AnimatronicState, AnimatronicModel> {
	private static final float MODEL_SCALE = 0.55f;
	private final Identifier texture;

	public static ModelLayerLocation layer(Animatronic.Kind kind) { return new ModelLayerLocation(Sigf.id(kind.id), "main"); }

	public AnimatronicRenderer(EntityRendererProvider.Context ctx, Animatronic.Kind kind) {
		super(ctx, new AnimatronicModel(ctx.bakeLayer(layer(kind))), 0.75f);
		this.texture = Sigf.id("textures/entity/" + kind.id + ".png");
	}

	@Override public AnimatronicState createRenderState() { return new AnimatronicState(); }
	@Override public Identifier getTextureLocation(AnimatronicState state) { return texture; }

	@Override
	public void extractRenderState(Animatronic entity, AnimatronicState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.scaring = entity.isScaring();
		state.frozen = entity.isFrozenByGaze();
		state.kind = entity.kind;
	}

	/** The model is built at double size for a sharper skin: shrink it and drop its feet back to the ground. */
	@Override
	protected void scale(AnimatronicState state, PoseStack pose) {
		pose.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
		pose.translate(0.0f, -1.5f, 0.0f);
	}
}
