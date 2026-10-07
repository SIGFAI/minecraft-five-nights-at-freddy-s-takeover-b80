package sigf.mod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import sigf.kit.Sigf;

/**
 * Server to client. mode 0: night state (a = hour x10, b = power %, c = night, d = flags: 1 door closed, 2 power out).
 * mode 1: jump scare (a = animatronic kind).
 */
public record FnafPayload(int mode, int a, int b, int c, int d) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<FnafPayload> TYPE = new CustomPacketPayload.Type<>(Sigf.id("night"));
	public static final StreamCodec<RegistryFriendlyByteBuf, FnafPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, FnafPayload::mode, ByteBufCodecs.VAR_INT, FnafPayload::a, ByteBufCodecs.VAR_INT, FnafPayload::b,
		ByteBufCodecs.VAR_INT, FnafPayload::c, ByteBufCodecs.VAR_INT, FnafPayload::d, FnafPayload::new);

	@Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}
