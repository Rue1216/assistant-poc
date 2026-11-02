package com.example.copilot.aiassistant.domain.answer;

import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import java.util.List;

/** The JSON Schema every model round must answer in (answer-citations spec 2.2). */
public final class AnswerSchema {

    public static final String NAME = "answer";

    private AnswerSchema() {
    }

    /**
     * @param citationIds ids issued so far (S1, S2, ...); when empty, sources are plain strings
     *                    and the backend drops them all while validating
     */
    public static ResponseFormat responseFormat(List<String> citationIds) {
        JsonSchemaElement sourceItem = citationIds.isEmpty()
                ? JsonStringSchema.builder().build()
                : JsonEnumSchema.builder().enumValues(citationIds).build();
        JsonObjectSchema block = JsonObjectSchema.builder()
                .addProperty("type", JsonEnumSchema.builder()
                        .enumValues("heading", "paragraph", "bullet", "numbered").build())
                .addProperty("text", JsonStringSchema.builder().build())
                .addProperty("sources", JsonArraySchema.builder().items(sourceItem).build())
                .required("type", "text", "sources")
                .additionalProperties(false)
                .build();
        JsonObjectSchema root = JsonObjectSchema.builder()
                .addProperty("blocks", JsonArraySchema.builder().items(block).build())
                .required("blocks")
                .additionalProperties(false)
                .build();
        return ResponseFormat.builder()
                .type(ResponseFormatType.JSON)
                .jsonSchema(JsonSchema.builder().name(NAME).rootElement(root).build())
                .build();
    }
}
