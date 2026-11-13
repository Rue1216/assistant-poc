package com.example.copilot.aiassistant.application.support;

/** Bean names of the transaction managers that {@code @Transactional(transactionManager = ...)} refers to. */
public final class Transactions {

    /** The MongoDB transaction manager (replica set required). */
    public static final String MONGO = "aiAssistantMongoTransactionManager";

    private Transactions() {
    }
}
