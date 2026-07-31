package wtf.oraculus.client.music.api;

import java.net.URI;

public record LanzouFile(String name, String size, String uploadTime, URI downloadUri) {
    public LanzouFile {
        name = name == null || name.isBlank() ? "Lanzou file" : name.trim();
        size = size == null ? "" : size.trim();
        uploadTime = uploadTime == null ? "" : uploadTime.trim();
        if (downloadUri == null || downloadUri.getHost() == null
                || !("http".equalsIgnoreCase(downloadUri.getScheme()) || "https".equalsIgnoreCase(downloadUri.getScheme()))) {
            throw new LanzouApiException("Lanzou API returned an invalid download URL");
        }
    }

}
