package wtf.oraculus.client.feature.helper.impl.player.rotation;

import wtf.oraculus.client.feature.helper.impl.player.rotation.handler.ClientRotationHandler;
import wtf.oraculus.client.feature.helper.impl.player.rotation.handler.RotationMouseHandler;

public final class RotationHelper {

    private RotationHelper() {
    }

    private static final ClientRotationHandler clientHandler = new ClientRotationHandler();
    private static final RotationMouseHandler mouseHandler = new RotationMouseHandler();

    public static RotationMouseHandler getHandler() {
        return mouseHandler;
    }

    public static ClientRotationHandler getClientHandler() {
        return clientHandler;
    }
}
