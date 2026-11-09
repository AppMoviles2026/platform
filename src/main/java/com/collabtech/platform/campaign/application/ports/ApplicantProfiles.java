package com.collabtech.platform.campaign.application.ports;
import java.util.*;
public interface ApplicantProfiles {
    Optional<Profile> find(UUID creatorProfileId);
    record Social(UUID id,String platform,String username) {}
    record Profile(UUID id,String name,String biography,String niche,String audienceDescription,String location,boolean active,List<Social> socialAccounts) {
        public Profile { socialAccounts=List.copyOf(socialAccounts); }
    }
}
