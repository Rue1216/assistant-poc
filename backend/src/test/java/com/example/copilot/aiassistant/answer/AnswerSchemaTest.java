package com.example.copilot.aiassistant.answer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnswerSchemaTest {

    @Test
    void enumListsIssuedIds() {
        ResponseFormat f = AnswerSchema.responseFormat(List.of("S1", "S2"));
        assertThat(f.type()).isEqualTo(ResponseFormatType.JSON);
        assertThat(f.jsonSchema().name()).isEqualTo("answer");
        JsonObjectSchema block = blockSchema(f);
        assertThat(block.required()).containsExactlyInAnyOrder("type", "text", "sources");
        assertThat(block.additionalProperties()).isFalse();
        assertThat(((JsonEnumSchema) block.properties().get("type")).enumValues())
                .containsExactly("heading", "paragraph", "bullet", "numbered");
        assertThat(block.properties().get("text")).isInstanceOf(JsonStringSchema.class);
        JsonArraySchema sources = (JsonArraySchema) block.properties().get("sources");
        assertThat(((JsonEnumSchema) sources.items()).enumValues()).containsExactly("S1", "S2");
    }

    @Test
    void noIdsMeansPlainStringItems() {
        JsonArraySchema sources = (JsonArraySchema) blockSchema(AnswerSchema.responseFormat(List.of()))
                .properties().get("sources");
        assertThat(sources.items()).isInstanceOf(JsonStringSchema.class);
    }

    @Test
    void rootRequiresBlocksOnly() {
        JsonObjectSchema root = (JsonObjectSchema) AnswerSchema.responseFormat(List.of()).jsonSchema().rootElement();
        assertThat(root.required()).containsExactly("blocks");
        assertThat(root.additionalProperties()).isFalse();
        assertThat(root.properties()).containsOnlyKeys("blocks");
    }

    private static JsonObjectSchema blockSchema(ResponseFormat f) {
        JsonObjectSchema root = (JsonObjectSchema) f.jsonSchema().rootElement();
        JsonArraySchema blocks = (JsonArraySchema) root.properties().get("blocks");
        return (JsonObjectSchema) blocks.items();
    }
}
