package com.example.copilot.aiassistant.domain.prompt;

/** Which published instructions {@link PromptAssembler} loads: all of them, none, or exactly one. */
public record InstructionSelection(Kind kind, String configKey) {

    public enum Kind { ALL, NONE, ONLY }

    private static final InstructionSelection ALL = new InstructionSelection(Kind.ALL, null);
    private static final InstructionSelection NONE = new InstructionSelection(Kind.NONE, null);

    public static InstructionSelection all() {
        return ALL;
    }

    public static InstructionSelection none() {
        return NONE;
    }

    public static InstructionSelection only(String configKey) {
        return new InstructionSelection(Kind.ONLY, configKey);
    }
}
