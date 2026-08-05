package wu_bo_han_is_fucking_hakuri;

public final class SessionBootstrap {
    private static final long TOKEN_MASK = 0x4f524143554c5553L;

    private SessionBootstrap() {
    }

    public static long install(final LegacyLoginEntry.LegacySession session, final long clientNonce) {
        if (session == null || !session.accepted() || session.token().length() != 64) {
            throw new SecurityException("Legacy authentication session was rejected");
        }
        long state = clientNonce ^ TOKEN_MASK;
        for (int index = 0; index < session.token().length(); index++) {
            state = Long.rotateLeft(state ^ session.token().charAt(index), 7) * 0x9e3779b97f4a7c15L;
        }
        return state ^ session.username().hashCode() ^ session.tier().hashCode();
    }
}
