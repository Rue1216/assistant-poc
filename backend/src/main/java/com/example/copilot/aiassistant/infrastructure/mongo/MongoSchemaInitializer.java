package com.example.copilot.aiassistant.infrastructure.mongo;

import com.example.copilot.aiassistant.application.support.ClasspathText;
import org.bson.Document;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.schema.JsonSchemaObject;
import org.springframework.stereotype.Component;

/** Idempotently creates the collections, validators and indexes (04 section 4). */
@Component
@Order(1)
public class MongoSchemaInitializer implements ApplicationRunner {

    static final String CONFIG = "COLL_ICRM_AI_ASSISTANT_CONFIG";
    static final String AUDIT = "COLL_ICRM_AI_ASSISTANT_AUDIT";
    static final String EVAL_RUN = "COLL_ICRM_AI_ASSISTANT_EVAL_RUN";
    /** 1,825 days, aligned with the platform audit log. */
    private static final long AUDIT_TTL_SECONDS = 157_680_000L;
    /** 180 days. */
    private static final long EVAL_RUN_TTL_SECONDS = 15_552_000L;

    private final MongoTemplate mongo;

    public MongoSchemaInitializer(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void run(ApplicationArguments args) {
        applyValidator(CONFIG, "mongo/config-validator.json", true);
        applyValidator(AUDIT, "mongo/audit-validator.json", false);
        applyValidator(EVAL_RUN, "mongo/eval-run-validator.json", true);
        createConfigIndexes();
        createAuditIndexes();
        createEvalRunIndexes();
    }

    private void applyValidator(String collection, String resource, boolean strictErrors) {
        Document validator = Document.parse(ClasspathText.read(resource));
        Document command = new Document(mongo.collectionExists(collection) ? "collMod" : "create", collection)
                .append("validator", validator);
        if (strictErrors) {
            command.append("validationLevel", "strict").append("validationAction", "error");
        }
        mongo.getDb().runCommand(command);
    }

    private void createConfigIndexes() {
        IndexOperations ops = mongo.indexOps(CONFIG);
        ops.createIndex(new Index().named("UK_ICRM_AI_CFG_1").on("configKey", Sort.Direction.ASC)
                .on("version", Sort.Direction.ASC).unique());
        ops.createIndex(new Index().named("UK_ICRM_AI_CFG_2").on("configKey", Sort.Direction.ASC).unique()
                .partial(PartialIndexFilter.of(Criteria.where("status").is("PUBLISHED"))));
        ops.createIndex(new Index().named("UK_ICRM_AI_CFG_3").on("configKey", Sort.Direction.ASC).unique()
                .partial(PartialIndexFilter.of(Criteria.where("status").is("DRAFT"))));
        ops.createIndex(new Index().named("IX_ICRM_AI_CFG_4").on("status", Sort.Direction.ASC)
                .on("configType", Sort.Direction.ASC));
        ops.createIndex(new Index().named("IX_ICRM_AI_CFG_5").on("status", Sort.Direction.ASC)
                .on("scheduledAt", Sort.Direction.ASC)
                .partial(PartialIndexFilter.of(Criteria.where("status").is("SCHEDULED"))));
    }

    private void createEvalRunIndexes() {
        IndexOperations ops = mongo.indexOps(EVAL_RUN);
        ops.createIndex(new Index().named("UK_ICRM_AI_EVAL_1").on("status", Sort.Direction.ASC).unique()
                .partial(PartialIndexFilter.of(Criteria.where("status").is("RUNNING"))));
        ops.createIndex(new Index().named("IX_ICRM_AI_EVAL_1").on("configKey", Sort.Direction.ASC)
                .on("startedAt", Sort.Direction.DESC));
        dropLegacyIndex(ops, "IX_ICRM_AI_EVAL_2");
        ops.createIndex(new Index().named("IX_ICRM_AI_EVAL_TTL").on("startedAt", Sort.Direction.ASC)
                .expire(EVAL_RUN_TTL_SECONDS));
    }

    private void createAuditIndexes() {
        IndexOperations ops = mongo.indexOps(AUDIT);
        dropLegacyIndex(ops, "IX_ICRM_AI_AUDIT_1");
        ops.createIndex(new Index().named("IX_ICRM_AI_AUDIT_TTL").on("createdAt", Sort.Direction.ASC)
                .expire(AUDIT_TTL_SECONDS));
        ops.createIndex(new Index().named("IX_ICRM_AI_AUDIT_2").on("userId", Sort.Direction.ASC)
                .on("createdAt", Sort.Direction.DESC));
        ops.createIndex(new Index().named("IX_ICRM_AI_AUDIT_4").on("traceId", Sort.Direction.ASC));
        ops.createIndex(new Index().named("IX_ICRM_AI_AUDIT_5").on("promptVersions.configKey", Sort.Direction.ASC)
                .on("promptVersions.version", Sort.Direction.ASC)
                .partial(PartialIndexFilter.of(Criteria.where("feedback").type(JsonSchemaObject.Type.STRING))));
    }

    /** A renamed index must go first: the same key under a new name is rejected while the old one exists. */
    private void dropLegacyIndex(IndexOperations ops, String name) {
        if (ops.getIndexInfo().stream().anyMatch(index -> index.getName().equals(name))) {
            ops.dropIndex(name);
        }
    }
}
