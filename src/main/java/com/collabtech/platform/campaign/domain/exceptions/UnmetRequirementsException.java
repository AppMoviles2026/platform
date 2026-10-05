package com.collabtech.platform.campaign.domain.exceptions;
import com.collabtech.platform.campaign.domain.model.entities.CampaignRequirement;
import java.util.List;
public final class UnmetRequirementsException extends RuntimeException {
    private final List<CampaignRequirement> requirements;
    public UnmetRequirementsException(List<CampaignRequirement> requirements) { super("Requirements not met"); this.requirements = List.copyOf(requirements); }
    public List<CampaignRequirement> requirements() { return requirements; }
}
