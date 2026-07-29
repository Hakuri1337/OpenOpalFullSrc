package wtf.oraculus.client.edition;

public final class EditionBuildInfo {
    private EditionBuildInfo() {
    }

    public static String getDisplayName() {
        return "Free";
    }

    public static boolean isFree() {
        return true;
    }

    public static String getApiName() {
        return "FREE";
    }
}
