package com.example.copilot.aiassistant.infrastructure.mongo;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import com.example.copilot.aiassistant.domain.config.ConfigStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

/**
 * Document-level writes behind publishing: archiving the live version, and claiming or reverting a SCHEDULED one.
 * It knows documents only (no domain aggregate, no assistant error codes), so it can move to a module shared with
 * the scheduler (backend/PORTING-scheduler.md section 5.1). Every update is conditional on {@code docVersion};
 * Mongo failures propagate unmapped.
 */
public class AssistantConfigPublishStore {

    private final MongoTemplate mongo;

    public AssistantConfigPublishStore(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /** SCHEDULED versions whose {@code scheduledAt} is at or before {@code now}. */
    public List<AssistantConfigDocument> findScheduledDue(Instant now) {
        Query query = Query.query(where("status").is(ConfigStatus.SCHEDULED).and("scheduledAt").lte(now));
        return mongo.find(query, AssistantConfigDocument.class);
    }

    /**
     * Archives the key's PUBLISHED version, if there is one. A targeted update of the status only: the archived
     * version keeps its updatedBy / updatedAt (= when it went live). Must run inside the caller's publishing
     * transaction; throws {@link OptimisticLockingFailureException} when that version changed since it was read.
     */
    public void archivePublished(String configKey) {
        Query published = Query.query(where("configKey").is(configKey).and("status").is(ConfigStatus.PUBLISHED));
        AssistantConfigDocument old = mongo.findOne(published, AssistantConfigDocument.class);
        if (old == null) {
            return;
        }
        Query current = Query.query(where("id").is(old.getId())
                .and("status").is(ConfigStatus.PUBLISHED)
                .and("docVersion").is(old.getDocVersion()));
        Update archive = new Update().set("status", ConfigStatus.ARCHIVED).inc("docVersion", 1L);
        if (mongo.updateFirst(current, archive, AssistantConfigDocument.class).getModifiedCount() != 1) {
            throw new OptimisticLockingFailureException("published config version changed before it was archived");
        }
    }

    /**
     * Archives the current PUBLISHED version and claims {@code due} with a conditional update (still SCHEDULED,
     * same docVersion), so that concurrent runs publish it once; {@code updatedBy} stays the person who scheduled
     * it. The caller runs it in a transaction. When another change won the race it throws
     * {@link OptimisticLockingFailureException} rather than returning, so that the transaction rolls the archive
     * back and the key is not left without a PUBLISHED version.
     */
    public void publishScheduled(AssistantConfigDocument due, Instant now) {
        archivePublished(due.getConfigKey());
        Update publish = new Update()
                .set("status", ConfigStatus.PUBLISHED)
                .set("updatedAt", now)
                .inc("docVersion", 1L);
        if (mongo.updateFirst(stillScheduled(due), publish, AssistantConfigDocument.class).getModifiedCount() != 1) {
            throw new OptimisticLockingFailureException("scheduled config version changed before it was published");
        }
    }

    /**
     * Turns {@code due} back into a DRAFT (clearing {@code scheduledAt}) if it is still SCHEDULED with the same
     * docVersion. Returns false, with nothing changed, when another change won the race. A single conditional write.
     */
    public boolean revertScheduled(AssistantConfigDocument due, String actor, Instant now) {
        Update revert = new Update()
                .set("status", ConfigStatus.DRAFT)
                .unset("scheduledAt")
                .set("updatedBy", actor)
                .set("updatedAt", now)
                .inc("docVersion", 1L);
        return mongo.updateFirst(stillScheduled(due), revert, AssistantConfigDocument.class).getModifiedCount() == 1;
    }

    private static Query stillScheduled(AssistantConfigDocument due) {
        return Query.query(where("id").is(due.getId())
                .and("status").is(ConfigStatus.SCHEDULED)
                .and("docVersion").is(due.getDocVersion()));
    }
}
