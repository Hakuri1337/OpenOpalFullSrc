package wtf.oraculus.client;

import wtf.oraculus.client.edition.EditionBuildInfo;

public final class ReleaseInfo {

    public static final String VERSION = "b8";
    public static final String NAME = "Oraculus";

    public static String getEditionLabel() {
        return EditionBuildInfo.getDisplayName();
    }

    public static String getBuildId() {
        return VERSION + "-" + EditionBuildInfo.getApiName().toLowerCase();
    }

}
