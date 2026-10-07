package sigf.mod;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/** What the renderer needs to know about an animatronic. */
public class AnimatronicState extends HumanoidRenderState {
	public boolean scaring, frozen;
	public Animatronic.Kind kind = Animatronic.Kind.FREDDY;
}
