package com.collabtech.platform.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.collabtech.platform.identity.application.handlers.GetPresentationQueryHandler;
import com.collabtech.platform.identity.application.projections.PresentationAudience;
import com.collabtech.platform.identity.application.projections.PresentationBenefitView;
import com.collabtech.platform.identity.application.projections.PublicPresentationView;
import com.collabtech.platform.identity.application.queries.GetPresentationQuery;
import java.util.List;
import org.junit.jupiter.api.Test;

class GetPresentationQueryHandlerTests {
    @Test
    void delegatesRequestedAudienceToContentProviderWithoutAccountsOrCampaigns() {
        var view = new PublicPresentationView(PresentationAudience.CREATOR, "es", "Title", "Proposal",
                List.of(new PresentationBenefitView("requirements", "Requirements", "Description")));
        var handler = new GetPresentationQueryHandler(audience -> {
            assertEquals(PresentationAudience.CREATOR, audience);
            return view;
        });
        assertSame(view, handler.handle(new GetPresentationQuery(PresentationAudience.CREATOR)));
    }

    @Test
    void rejectsMissingAudience() {
        assertThrows(NullPointerException.class, () -> new GetPresentationQuery(null));
    }

    @Test
    void rejectsMissingProviderOrQuery() {
        assertThrows(NullPointerException.class, () -> new GetPresentationQueryHandler(null));
        var handler = new GetPresentationQueryHandler(audience -> null);
        assertThrows(NullPointerException.class, () -> handler.handle(null));
    }
}
