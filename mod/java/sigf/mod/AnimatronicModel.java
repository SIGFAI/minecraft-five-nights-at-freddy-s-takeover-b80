package sigf.mod;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Chunky 3D mascot model at twice the vanilla humanoid resolution (the renderer scales it back down): 128x128 skin,
 * plus ears, snouts, beaks, a top hat or a hook as extra cubes. The lower half of the skin holds four solid swatches for them.
 */
public final class AnimatronicModel extends HumanoidModel<AnimatronicState> {
	private static final int[] FUR = {0, 64}, ACC = {64, 64}, DARK = {0, 96}, INNER = {64, 96};

	public AnimatronicModel(ModelPart root) { super(root); }

	private static PartDefinition cube(PartDefinition parent, String name, int[] swatch, float x, float y, float z, float w, float h, float d, PartPose pose, boolean mirror) {
		return parent.addOrReplaceChild(name, CubeListBuilder.create().texOffs(swatch[0], swatch[1]).mirror(mirror).addBox(x, y, z, w, h, d), pose);
	}

	public static LayerDefinition layer(Animatronic.Kind kind) {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		CubeDeformation g = CubeDeformation.NONE;
		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-8, -16, -8, 16, 16, 16, g), PartPose.ZERO);
		head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(32, 32).addBox(-8, 0, -4, 16, 24, 8, g), PartPose.ZERO);
		PartDefinition rArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(80, 32).addBox(-6, -4, -4, 8, 24, 8, g), PartPose.offset(-10, 4, 0));
		root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(80, 32).mirror().addBox(-2, -4, -4, 8, 24, 8, g), PartPose.offset(10, 4, 0));
		root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 32).addBox(-4, 0, -4, 8, 24, 8, g), PartPose.offset(-3.8f, 24, 0));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(0, 32).mirror().addBox(-4, 0, -4, 8, 24, 8, g), PartPose.offset(3.8f, 24, 0));

		switch (kind) {
			case FREDDY -> {
				for (int s : new int[] {-1, 1}) {
					float ex = s < 0 ? -11 : 4;
					cube(head, "ear" + s, FUR, ex, -21, -2, 7, 7, 3, PartPose.ZERO, false);
					cube(head, "ear_in" + s, INNER, ex + 1.5f, -19.5f, -2.6f, 4, 4, 1, PartPose.ZERO, false);
				}
				cube(head, "snout", ACC, -4.5f, -6.5f, -11.5f, 9, 6.5f, 4, PartPose.ZERO, false);
				cube(head, "nose", DARK, -2.2f, -7.8f, -12.2f, 4.4f, 2.6f, 1.2f, PartPose.ZERO, false);
				cube(head, "hat", DARK, -6, -26.5f, -6, 12, 10, 12, PartPose.ZERO, false);
				cube(head, "brim", DARK, -8.5f, -17.5f, -8.5f, 17, 2, 17, PartPose.ZERO, false);
				cube(body, "bowtie", DARK, -5.5f, 0, -5.4f, 11, 4.5f, 2, PartPose.ZERO, false);
			}
			case BONNIE -> {
				for (int s : new int[] {-1, 1}) {
					PartPose p = PartPose.offsetAndRotation(s * 4.5f, -16, 0, 0, 0, s * -0.14f);
					PartDefinition ear = cube(head, "ear" + s, FUR, -2.5f, -19, -1.5f, 5, 19, 3, p, false);
					cube(ear, "ear_in" + s, ACC, -1.3f, -17.5f, -2.3f, 2.6f, 15, 1, PartPose.ZERO, false);
				}
				cube(head, "snout", ACC, -4.5f, -6.5f, -11.5f, 9, 6.5f, 4, PartPose.ZERO, false);
				cube(head, "nose", DARK, -2f, -7.6f, -12.2f, 4, 2.4f, 1.2f, PartPose.ZERO, false);
				cube(body, "bowtie", INNER, -5.5f, 0, -5.4f, 11, 4.5f, 2, PartPose.ZERO, false);
			}
			case CHICA -> {
				cube(head, "beak_up", ACC, -4, -8.5f, -13, 8, 3, 5.5f, PartPose.ZERO, false);
				cube(head, "beak_low", ACC, -3.5f, -5.5f, -12, 7, 2, 4.5f, PartPose.ZERO, false);
				for (int i = -1; i <= 1; i++)
					cube(head, "tuft" + i, FUR, -0.8f, -22.5f, -0.8f, 1.6f, 6.5f, 1.6f, PartPose.offsetAndRotation(i * 1.2f, 0, 0, 0, 0, i * 0.4f), false);
			}
			case FOXY -> {
				for (int s : new int[] {-1, 1}) {
					PartPose p = PartPose.offsetAndRotation(s * 5.5f, -16, 0, 0, 0, s * -0.18f);
					PartDefinition ear = cube(head, "ear" + s, FUR, -2.8f, -9, -1.5f, 5.6f, 9, 3, p, false);
					cube(ear, "ear_in" + s, ACC, -1.6f, -7.5f, -2.2f, 3.2f, 6, 1, PartPose.ZERO, false);
				}
				cube(head, "snout", ACC, -3.8f, -6.8f, -15, 7.6f, 5, 7.5f, PartPose.ZERO, false);
				cube(head, "nose", DARK, -1.7f, -7.8f, -15.4f, 3.4f, 2.2f, 1.2f, PartPose.ZERO, false);
				cube(head, "jaw", ACC, -3.4f, 0, -7.5f, 6.8f, 2.2f, 7.5f, PartPose.offsetAndRotation(0, -2.3f, -6.5f, 0.5f, 0, 0), false);
				// The hook in place of the right hand.
				cube(rArm, "hook_shaft", INNER, -3.5f, 19, -1, 2.4f, 6, 2.4f, PartPose.ZERO, false);
				cube(rArm, "hook_curve", INNER, -3.5f, 23.6f, -5.5f, 2.4f, 2.4f, 7, PartPose.ZERO, false);
				cube(rArm, "hook_tip", INNER, -3.5f, 19.5f, -5.5f, 2.4f, 5, 2.4f, PartPose.ZERO, false);
			}
		}
		return LayerDefinition.create(mesh, 128, 128);
	}

	@Override
	public void setupAnim(AnimatronicState s) {
		super.setupAnim(s);
		boolean walking = s.walkAnimationSpeed > 0.05f && !s.frozen;
		if (s.scaring) {
			float shake = Mth.sin(s.ageInTicks * 2.4f) * 0.12f;
			rightArm.xRot = leftArm.xRot = -1.55f + shake;
			rightArm.zRot = 0.25f; leftArm.zRot = -0.25f;
			head.xRot = -0.25f;
		} else if (s.frozen) {
			// Caught mid-step: stiff pose, head tilted like a puppet.
			head.zRot = 0.22f;
			head.xRot = 0.1f;
		} else if (walking) {
			rightArm.xRot = -0.7f + rightArm.xRot * 0.3f;
			leftArm.xRot = -0.7f + leftArm.xRot * 0.3f;
			if (s.kind == Animatronic.Kind.FOXY) { body.xRot = 0.35f; head.xRot = 0.15f; }
		}
	}
}
