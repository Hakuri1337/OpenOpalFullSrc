package wtf.oraculus.client.edition;

public final class EditionBuildInfo {
    private EditionBuildInfo() {
    }

    public static String getDisplayName() {
        return "Beta";
    }

    public static boolean isFree() {
        return false;
    }

    public static String getApiName() {
        return "BETA";
    }
}
