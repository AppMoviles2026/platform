package com.collabtech.platform.campaign.application.ports;
import java.util.function.Supplier;
public interface CampaignUnitOfWork { <T> T execute(Supplier<T> work); }
