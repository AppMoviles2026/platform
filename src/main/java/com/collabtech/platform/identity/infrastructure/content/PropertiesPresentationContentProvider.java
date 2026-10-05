package com.collabtech.platform.identity.infrastructure.content;

import com.collabtech.platform.identity.application.ports.PresentationContentProvider;
import com.collabtech.platform.identity.application.projections.PresentationAudience;
import com.collabtech.platform.identity.application.projections.PresentationBenefitView;
import com.collabtech.platform.identity.application.projections.PublicPresentationView;
import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/** Real, immutable catalog loaded from versioned content; not preview data or a database mock. */
public final class PropertiesPresentationContentProvider implements PresentationContentProvider {
    private final Map<PresentationAudience, PublicPresentationView> presentations;

    public PropertiesPresentationContentProvider(Reader reader) throws IOException {
        var properties = new Properties();
        properties.load(reader);
        var loaded = new EnumMap<PresentationAudience, PublicPresentationView>(PresentationAudience.class);
        loaded.put(PresentationAudience.BRAND, read(properties, PresentationAudience.BRAND, "brand"));
        loaded.put(PresentationAudience.CREATOR, read(properties, PresentationAudience.CREATOR, "creator"));
        this.presentations = Map.copyOf(loaded);
    }

    @Override
    public PublicPresentationView findByAudience(PresentationAudience audience) {
        return presentations.get(Objects.requireNonNull(audience, "audience"));
    }

    private static PublicPresentationView read(Properties properties, PresentationAudience audience, String prefix) {
        var benefits = Arrays.stream(required(properties, prefix + ".benefits").split(","))
                .map(String::strip)
                .map(code -> new PresentationBenefitView(code, required(properties, prefix + ".benefit." + code + ".title"),
                        required(properties, prefix + ".benefit." + code + ".description")))
                .toList();
        return new PublicPresentationView(audience, required(properties, "language"),
                required(properties, prefix + ".title"),
                required(properties, prefix + ".valueProposition"), benefits);
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing public presentation content: " + key);
        }
        return value.strip();
    }
}
