package wtf.oraculus.utility.misc.system;

import org.lwjgl.util.tinyfd.TinyFileDialogs;



public final class DialogUtility {

    private DialogUtility() {
    }

    public static boolean notify(final String type, final String icon, final String title, final String message) {
        return TinyFileDialogs.tinyfd_messageBox(title, message, type, icon, true);
    }

}
