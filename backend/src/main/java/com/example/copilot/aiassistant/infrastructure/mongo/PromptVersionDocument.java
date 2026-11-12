package com.example.copilot.aiassistant.infrastructure.mongo;

import org.springframework.data.mongodb.core.mapping.Field;

import lombok.Getter;
import lombok.Setter;

/**
 * Stored form of {@code PromptVersion}; embedded in audit and eval-run documents.
 */
@Getter
@Setter
public class PromptVersionDocument {

    /** Config key of the published config that went into the prompt */
    @Field(write = Field.Write.ALWAYS)
    private String configKey;
    /** Published version number */
    private int version;
}
