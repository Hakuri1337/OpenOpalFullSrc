package wtf.oraculus.utility.player.protocol;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;

final class ViaFabricPlus18Api {
    private ViaFabricPlus18Api() {
    }

    static boolean isTargeting1_8() {
        return ProtocolVersion.v1_8.equalTo(ViaFabricPlus.getImpl().getTargetVersion());
    }

    static String getTargetVersionName() {
        final ProtocolVersion targetVersion = ViaFabricPlus.getImpl().getTargetVersion();
        return targetVersion == null ? "unknown" : targetVersion.getName();
    }

    static Object getTargetVersion() {
        return ViaFabricPlus.getImpl().getTargetVersion();
    }

    static boolean setTargetVersion1_8() {
        ViaFabricPlus.getImpl().setTargetVersion(ProtocolVersion.v1_8);
        return isTargeting1_8();
    }

    static boolean restoreTargetVersion(final Object targetVersion) {
        if (!(targetVersion instanceof ProtocolVersion protocolVersion)) {
            return false;
        }
        ViaFabricPlus.getImpl().setTargetVersion(protocolVersion);
        return protocolVersion.equalTo(ViaFabricPlus.getImpl().getTargetVersion());
    }
}
