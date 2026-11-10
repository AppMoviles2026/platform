package com.collabtech.platform.performance.infrastructure.providers;

import com.collabtech.platform.performance.application.ports.PerformancePorts.SocialMetricsProvider;
import com.collabtech.platform.performance.domain.model.PerformanceModels.*;
import com.collabtech.platform.identity.interfaces.acl.SocialAuthorizationFacade;
import com.collabtech.platform.identity.application.exceptions.IdentityFailure;
import java.time.Clock;
import java.time.Instant;
import java.time.DateTimeException;
import java.util.*;
import org.springframework.http.MediaType;
import org.springframework.web.client.*;
import tools.jackson.databind.JsonNode;

/** TikTok Display API verifies ownership; no arbitrary input URL is ever fetched. */
public final class AuthorizedSocialMetricsProvider implements SocialMetricsProvider {
    public static final String QUERY="https://open.tiktokapis.com/v2/video/query/?fields=id,create_time,view_count,like_count,comment_count,share_count";
    private final SocialAuthorizationFacade authorization;private final RestClient http;private final Clock clock;
    public AuthorizedSocialMetricsProvider(SocialAuthorizationFacade authorization,RestClient http,Clock clock) {
        this.authorization=authorization;this.http=http;this.clock=clock;
    }
    public FetchResult fetch(UUID creator,UUID social,String platform,String post) {
        if(!platform.equals("tiktok"))return FetchResult.unavailable(FetchStatus.UNSUPPORTED_PROVIDER,platform);
        try {
            return authorization.withAuthorizedAccess(creator,social,Set.of("video.list"),access->{
                if(!access.platform().equals(platform))return FetchResult.unavailable(FetchStatus.INVALID_RESPONSE,platform);
                try {
                    var response=http.post().uri(QUERY).header("Authorization","Bearer "+access.accessToken())
                            .contentType(MediaType.APPLICATION_JSON).body(Map.of("filters",Map.of("video_ids",List.of(post))))
                            .retrieve().body(JsonNode.class);
                    return parse(response,post);
                }catch(RestClientResponseException error) {
                    int status=error.getStatusCode().value();
                    return FetchResult.unavailable(status==401?FetchStatus.AUTHORIZATION_REQUIRED:status==403?FetchStatus.INSUFFICIENT_SCOPE:
                            status==429?FetchStatus.RATE_LIMITED:FetchStatus.PROVIDER_UNAVAILABLE,platform);
                }catch(RestClientException error){return FetchResult.unavailable(FetchStatus.PROVIDER_UNAVAILABLE,platform);}
            });
        }catch(IdentityFailure error) {
            return FetchResult.unavailable(switch(error.code()){
                case SOCIAL_SCOPE_REQUIRED->FetchStatus.INSUFFICIENT_SCOPE;
                case PROVIDER_NOT_CONFIGURED->FetchStatus.PROVIDER_NOT_CONFIGURED;
                case PROVIDER_FAILED->FetchStatus.PROVIDER_UNAVAILABLE;
                default->FetchStatus.AUTHORIZATION_REQUIRED;
            },platform);
        }
    }
    private FetchResult parse(JsonNode response,String id) {
        if(response==null||!response.path("error").path("code").isTextual())return unavailable(FetchStatus.INVALID_RESPONSE);
        String code=response.path("error").path("code").asText();
        if(!code.equals("ok"))return unavailable(code.contains("token")?FetchStatus.AUTHORIZATION_REQUIRED:code.contains("scope")?FetchStatus.INSUFFICIENT_SCOPE:
                code.contains("rate")?FetchStatus.RATE_LIMITED:FetchStatus.PROVIDER_UNAVAILABLE);
        var videos=response.path("data").path("videos");
        if(!videos.isArray()||videos.size()>1)return unavailable(FetchStatus.INVALID_RESPONSE);
        if(videos.isEmpty())return unavailable(FetchStatus.NO_DATA);
        var video=videos.get(0);
        if(!video.path("id").asText().equals(id))return unavailable(FetchStatus.INVALID_RESPONSE);
        var created=video.path("create_time");
        if(!created.isIntegralNumber()||!created.canConvertToLong()||created.asLong()<0)return unavailable(FetchStatus.INVALID_RESPONSE);
        var now=clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        Instant start;
        try{start=Instant.ofEpochSecond(created.asLong());}catch(DateTimeException error){return unavailable(FetchStatus.INVALID_RESPONSE);}
        if(start.isAfter(now))return unavailable(FetchStatus.INVALID_RESPONSE);
        var values=new EnumMap<MetricType,Long>(MetricType.class);
        var fields=Map.of("view_count",MetricType.VIEWS,"like_count",MetricType.LIKES,"comment_count",MetricType.COMMENTS,"share_count",MetricType.SHARES);
        for(var entry:fields.entrySet()) {
            var value=video.path(entry.getKey());
            if(value.isMissingNode()||value.isNull())continue;
            if(!value.isIntegralNumber()||!value.canConvertToLong()||value.asLong()<0)return unavailable(FetchStatus.INVALID_RESPONSE);
            values.put(entry.getValue(),value.asLong());
        }
        return values.isEmpty()?unavailable(FetchStatus.NO_DATA):new FetchResult(FetchStatus.AVAILABLE,"tiktok",new Period(start,now),values);
    }
    private FetchResult unavailable(FetchStatus status){return FetchResult.unavailable(status,"tiktok");}
}
