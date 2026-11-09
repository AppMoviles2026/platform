package com.collabtech.platform.collaboration.infrastructure.persistence;

import com.collabtech.platform.collaboration.application.ports.CollaborationPorts.*;
import com.collabtech.platform.collaboration.domain.model.*;
import com.collabtech.platform.collaboration.domain.exceptions.CollaborationFailure;
import com.collabtech.platform.collaboration.domain.exceptions.CollaborationFailure.Code;
import com.collabtech.platform.shared.application.pagination.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import tools.jackson.databind.ObjectMapper;

public class CollaborationPersistence implements Reads {
    private final EntityManager em;private final ObjectMapper json;
    public CollaborationPersistence(EntityManager em,ObjectMapper json){this.em=em;this.json=json;}
    public Optional<Agreement> agreement(UUID id,boolean lock){
        var row=lock?em.find(AgreementJpaEntity.class,id.toString(),LockModeType.PESSIMISTIC_WRITE):em.find(AgreementJpaEntity.class,id.toString());
        if(row!=null&&lock)em.refresh(row,LockModeType.PESSIMISTIC_WRITE);return Optional.ofNullable(row).map(this::agreement);
    }
    public Optional<Agreement> agreementForApplication(UUID id,boolean lock){
        var q=em.createQuery("select a from AgreementJpaEntity a where a.applicationId=:id",AgreementJpaEntity.class).setParameter("id",id.toString());
        if(lock)q.setLockMode(LockModeType.PESSIMISTIC_WRITE);
        return q.getResultStream().findFirst().map(row->{if(lock)em.refresh(row,LockModeType.PESSIMISTIC_WRITE);return agreement(row);});
    }
    public Agreement save(Agreement a){
        var row=em.find(AgreementJpaEntity.class,a.id().toString());boolean create=row==null;
        if(row==null){row=new AgreementJpaEntity();row.id=a.id().toString();row.applicationId=a.snapshot().applicationId().toString();
            row.brandId=a.snapshot().brandId().toString();row.creatorId=a.snapshot().creatorId().toString();row.snapshot=json.writeValueAsString(a.snapshot());
            row.deadline=a.snapshot().acceptanceDeadline();row.createdAt=a.snapshot().capturedAt();
        }else if(row.version!=a.version())throw new CollaborationFailure(Code.CONCURRENT_UPDATE);
        row.status=a.status().name();row.brandAt=a.brandAcceptedAt();row.brandBy=string(a.brandAcceptedBy());row.creatorAt=a.creatorAcceptedAt();row.creatorBy=string(a.creatorAcceptedBy());
        row.decidedAt=a.decidedAt();row.rejectedBy=string(a.rejectedBy());if(create)em.persist(row);em.flush();a.restoreVersion(row.version);return a;
    }
    public Optional<Collaboration> collaboration(UUID id,boolean lock){
        var row=lock?em.find(CollaborationJpaEntity.class,id.toString(),LockModeType.PESSIMISTIC_WRITE):em.find(CollaborationJpaEntity.class,id.toString());
        if(row!=null&&lock)em.refresh(row,LockModeType.PESSIMISTIC_WRITE);return Optional.ofNullable(row).map(this::collaboration);
    }
    public Optional<Collaboration> collaborationForAgreement(UUID id){return em.createQuery("select c from CollaborationJpaEntity c where c.agreementId=:id",CollaborationJpaEntity.class)
            .setParameter("id",id.toString()).getResultStream().findFirst().map(this::collaboration);}
    public Collaboration save(Collaboration c){
        var row=em.find(CollaborationJpaEntity.class,c.id().toString());boolean create=row==null;
        if(row==null){row=new CollaborationJpaEntity();row.id=c.id().toString();row.agreementId=c.agreementId().toString();row.brandId=c.snapshot().brandId().toString();
            row.creatorId=c.snapshot().creatorId().toString();row.snapshot=json.writeValueAsString(c.snapshot());row.startedAt=c.startedAt();
        }else if(row.version!=c.version())throw new CollaborationFailure(Code.CONCURRENT_UPDATE);
        row.obligations=json.writeValueAsString(c.obligations());row.status=c.status().name();row.deadline=c.nextDeadline();row.expiredAt=c.expiredAt();if(create)em.persist(row);em.flush();c.restoreVersion(row.version);return c;
    }
    public PageResult<Agreement> agreements(Actor actor,Agreement.Status status,PageRequest page,Instant now){
        String where="a."+field(actor)+"=:owner";boolean temporal=false;
        if(status==Agreement.Status.EXPIRED){where+=" and (a.status='EXPIRED' or (a.status='PENDING' and a.deadline<=:now))";temporal=true;}
        else if(status==Agreement.Status.PENDING){where+=" and a.status='PENDING' and a.deadline>:now";temporal=true;}
        else if(status!=null)where+=" and a.status='"+status.name()+"'";
        var rows=em.createQuery("select a from AgreementJpaEntity a where "+where+" order by a.createdAt desc,a.id",AgreementJpaEntity.class).setParameter("owner",actor.profileId().toString());
        var count=em.createQuery("select count(a) from AgreementJpaEntity a where "+where,Long.class).setParameter("owner",actor.profileId().toString());
        if(temporal){rows.setParameter("now",now);count.setParameter("now",now);}
        return new PageResult<>(rows.setFirstResult(offset(page)).setMaxResults(page.size()).getResultList().stream().map(this::agreement).toList(),count.getSingleResult(),page.page(),page.size());
    }
    public PageResult<Collaboration> collaborations(Actor actor,Collaboration.Status status,PageRequest page,Instant now){
        String where="c."+field(actor)+"=:owner";boolean temporal=false;
        if(status==Collaboration.Status.EXPIRED){where+=" and (c.status='EXPIRED' or (c.status in ('ACTIVE','UNDER_REVIEW') and c.deadline<=:now))";temporal=true;}
        else if(status==Collaboration.Status.ACTIVE||status==Collaboration.Status.UNDER_REVIEW){where+=" and c.status='"+status.name()+"' and (c.deadline is null or c.deadline>:now)";temporal=true;}
        else if(status!=null)where+=" and c.status='"+status.name()+"'";
        var rows=em.createQuery("select c from CollaborationJpaEntity c where "+where+" order by c.startedAt desc,c.id",CollaborationJpaEntity.class).setParameter("owner",actor.profileId().toString());
        var count=em.createQuery("select count(c) from CollaborationJpaEntity c where "+where,Long.class).setParameter("owner",actor.profileId().toString());
        if(temporal){rows.setParameter("now",now);count.setParameter("now",now);}
        return new PageResult<>(rows.setFirstResult(offset(page)).setMaxResults(page.size()).getResultList().stream().map(this::collaboration).toList(),count.getSingleResult(),page.page(),page.size());
    }
    public List<UUID> dueAgreements(Instant now,int limit){return em.createQuery("select a.id from AgreementJpaEntity a where a.status='PENDING' and a.deadline<=:now order by a.deadline,a.id",String.class)
            .setParameter("now",now).setMaxResults(limit).getResultList().stream().map(UUID::fromString).toList();}
    public List<UUID> dueCollaborations(Instant now,int limit){return em.createQuery("select c.id from CollaborationJpaEntity c where c.status in ('ACTIVE','UNDER_REVIEW') and c.deadline<=:now order by c.deadline,c.id",String.class)
            .setParameter("now",now).setMaxResults(limit).getResultList().stream().map(UUID::fromString).toList();}
    private Agreement agreement(AgreementJpaEntity r){return new Agreement(UUID.fromString(r.id),json.readValue(r.snapshot,AgreementSnapshot.class),Agreement.Status.valueOf(r.status),r.brandAt,uuid(r.brandBy),r.creatorAt,uuid(r.creatorBy),r.decidedAt,uuid(r.rejectedBy),r.version);}
    private Collaboration collaboration(CollaborationJpaEntity r){return new Collaboration(UUID.fromString(r.id),UUID.fromString(r.agreementId),json.readValue(r.snapshot,AgreementSnapshot.class),r.startedAt,Collaboration.Status.valueOf(r.status),
            json.readValue(r.obligations,json.getTypeFactory().constructCollectionType(List.class,Collaboration.Obligation.class)),r.expiredAt,r.version);}
    private static String field(Actor a){return a.party()==Agreement.Party.BRAND?"brandId":"creatorId";}
    private static int offset(PageRequest p){long n=(long)p.page()*p.size();if(n>Integer.MAX_VALUE)throw new IllegalArgumentException("Invalid page");return (int)n;}
    private static UUID uuid(String value){return value==null?null:UUID.fromString(value);}private static String string(UUID value){return value==null?null:value.toString();}
}
