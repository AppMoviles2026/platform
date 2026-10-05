package com.collabtech.platform.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.collabtech.platform.identity.application.projections.PresentationAudience;
import com.collabtech.platform.identity.infrastructure.content.PropertiesPresentationContentProvider;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class PresentationContentProviderTests {
    @Test
    void loadsBothPublishedAudiencesWithUtf8AndImmutableBenefits() throws IOException {
        var provider = new PropertiesPresentationContentProvider(new StringReader(publishedCopy()));
        var brand = provider.findByAudience(PresentationAudience.BRAND);
        var creator = provider.findByAudience(PresentationAudience.CREATOR);
        assertEquals("Más control para tu marca", brand.title());
        assertEquals("Más oportunidades para tu contenido", creator.title());
        assertEquals(5, brand.benefits().size());
        assertEquals(6, creator.benefits().size());
        assertThrows(UnsupportedOperationException.class, () -> brand.benefits().clear());
    }

    @Test
    void failsFastWhenRequiredCopyIsMissing() throws IOException {
        String copy = publishedCopy().replaceAll("(?m)^brand\\.title=.*(?:\\r?\\n|$)", "");
        var exception = assertThrows(IllegalStateException.class,
                () -> new PropertiesPresentationContentProvider(new StringReader(copy)));
        assertTrue(exception.getMessage().contains("brand.title"));
    }

    @Test
    void failsFastWhenBenefitCodesAreDuplicated() throws IOException {
        String copy = publishedCopy().replace("brand.benefits=campaigns,requirements,deliverables,compensation,tracking",
                "brand.benefits=campaigns,campaigns");
        assertThrows(IllegalArgumentException.class,
                () -> new PropertiesPresentationContentProvider(new StringReader(copy)));
    }

    @Test
    void failsFastWhenBenefitDescriptionIsBlank() throws IOException {
        String copy = publishedCopy().replaceAll("(?m)^creator\\.benefit\\.dates\\.description=.*$",
                "creator.benefit.dates.description= ");
        var exception = assertThrows(IllegalStateException.class,
                () -> new PropertiesPresentationContentProvider(new StringReader(copy)));
        assertTrue(exception.getMessage().contains("creator.benefit.dates.description"));
    }

    private static String publishedCopy() throws IOException {
        var stream = Objects.requireNonNull(PresentationContentProviderTests.class.getResourceAsStream(
                "/identity/presentations/presentations_es.properties"));
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var copy = new StringBuilder();
            var buffer = new char[1024];
            int count;
            while ((count = reader.read(buffer)) != -1) copy.append(buffer, 0, count);
            return copy.toString();
        }
    }
}
