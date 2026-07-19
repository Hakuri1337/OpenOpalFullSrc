package wtf.oraculus.utility.misc;

public final class StringUtility {

    private StringUtility() {
    }

    // rotate(13, ...) = rot13
    public static String rotate(int shift, final String str) {
        final StringBuilder builder = new StringBuilder();
        shift = shift % 26;

        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);

            // lowercase
            if (c >= 'a' && c <= 'z') {
                // lowercase
                c = (char) ('a' + (c - 'a' + shift + 26) % 26);
            } else if (c >= 'A' && c <= 'Z') {
                // uppercase
                c = (char) ('A' + (c - 'A' + shift + 26) % 26);
            } else {
//                c = (char) (c + shift + 58);
            }

            builder.append(c);
        }

        return builder.toString();
    }

}
