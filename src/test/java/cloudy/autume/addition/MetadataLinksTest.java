package cloudy.autume.addition;

import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class MetadataLinksTest {
    @Test
    void exposesLauncherAndModMenuLinks() throws Exception {
        var resources = getClass().getClassLoader().getResources("fabric.mod.json");
        JsonObject metadata = null;
        while (resources.hasMoreElements() && metadata == null) {
            try (var stream = resources.nextElement().openStream()) {
                var candidate = JsonParser.parseReader(
                        new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                if ("qcloudy_addition".equals(candidate.get("id").getAsString())) {
                    metadata = candidate;
                }
            }
        }
        assertNotNull(metadata);

        var contact = metadata.getAsJsonObject("contact");
        assertEquals("https://qcloudy.net/", contact.get("homepage").getAsString());
        assertEquals("https://github.com/northwestcloudy/QCloudy-Addition",
                contact.get("sources").getAsString());
        assertEquals("https://github.com/northwestcloudy/QCloudy-Addition/issues",
                contact.get("issues").getAsString());

        var links = metadata.getAsJsonObject("custom")
                .getAsJsonObject("modmenu")
                .getAsJsonObject("links");
        assertEquals("https://qcloudy.net/",
                links.get("qcloudy_addition.modmenu.official_website").getAsString());
        assertEquals("https://qcloudy.net/download/",
                links.get("qcloudy_addition.modmenu.downloads").getAsString());
        assertEquals(2, links.size());
    }
}
