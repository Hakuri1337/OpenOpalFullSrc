package wtf.oraculus.client.feature.module.impl.utility.antistaff;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

public final class OraculusStaffList {

    private static final String STAFF_LIST_B64 = "QuermeaQnOaXoOmHj+Wfn+mbqizkuInlm73mnYAs56yZ5qmZLE1lbmdDaGVuMzg4NCxBbmRyZXdrcmlzdCxGaWE5LOaeq+iQp+ael+eEtiznu7/osYbkuYPjgZXjgpMs5oqW6Z+z5Li25bCP5YyqLOaKlumfs19hd2Hpqazljp8sTW5hbUxlb18s5Lit5LqM5bCR5bm0REws5p6V5LiK5Lmm5Li25aGR5pyb5pyILElhbU1vbGluY2VuXywsQ29GdV9fLOaWl+aImOiDnOS9myzlj6rnjqnmlqXlgJks5p6V5LiK5Lmm5Li26Zuq5aScLGFpeXVraSxDYW5keUFwb3N0bGUsY2h1bnlpMSzmtYHlvbHlj6rkvJrlmKTlmKTlmKQscXRlc2RmXzY3NCxxeHRtbGM5OSxTa3lmb3ks56We5Z2R5LmL6YCXLOWco+S4iuiNo+iAgDIzMyzlsI/lhpvlkJvkuLblpKnkvb/kuYvnv7ws5p6V5LiK5Lmm5Li25YKy5a+SLF93aW5uZXJfLFNreV9ZdWFueGlhbw==";

    private static final String DECODED = new String(java.util.Base64.getDecoder().decode(STAFF_LIST_B64), StandardCharsets.UTF_8);

    private static final Set<String> NAMES = Arrays.stream(DECODED.split(",", -1))
            .map(String::trim)
            .filter(name -> !name.isEmpty())
            .collect(LinkedHashSet::new, Set::add, Set::addAll);

    private OraculusStaffList() {
    }

    public static boolean contains(final String name) {
        return name != null && !name.isBlank() && NAMES.contains(name.trim());
    }

    public static Set<String> names() {
        return Set.copyOf(NAMES);
    }

    public static String decoded() {
        return DECODED;
    }
}
