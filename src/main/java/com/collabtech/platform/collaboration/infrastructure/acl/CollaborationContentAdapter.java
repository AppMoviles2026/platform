package com.collabtech.platform.collaboration.infrastructure.acl;
import com.collabtech.platform.collaboration.interfaces.acl.CollaborationContentFacade;
import com.collabtech.platform.collaboration.domain.model.DeliveryContent;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
@Component @Profile("!skeleton") @Transactional(readOnly=true)
public class CollaborationContentAdapter implements CollaborationContentFacade {
    private final JdbcTemplate jdbc;private final ObjectMapper json;
    public CollaborationContentAdapter(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
    public Optional<Content> find(UUID collaboration,UUID submission) {
        return jdbc.query("""
                select s.submission_id,s.deliverable_id,s.content,s.revision,d.current_revision,d.status
                from collaboration_submission s join collaboration_deliverable d on d.deliverable_id=s.deliverable_id
                where d.collaboration_id=? and s.submission_id=?
                """,(r,i)->new Content(UUID.fromString(r.getString(1)),UUID.fromString(r.getString(2)),
                json.readValue(r.getString(3),DeliveryContent.class).content().url(),r.getInt(4)==r.getInt(5),r.getString(6)),
                collaboration.toString(),submission.toString()).stream().findFirst();
    }
    public Set<UUID> currentSubmissions(UUID collaboration) {
        return Set.copyOf(jdbc.query("""
                select s.submission_id from collaboration_submission s join collaboration_deliverable d on d.deliverable_id=s.deliverable_id
                where d.collaboration_id=? and s.revision=d.current_revision and d.status in ('PENDING_VALIDATION','APPROVED')
                """,(r,i)->UUID.fromString(r.getString(1)),collaboration.toString()));
    }
}
