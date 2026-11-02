package com.example.copilot.aiassistant.application.support;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

public final class ClasspathText {

    private ClasspathText() {
    }

    public static String read(String path) {
        try (InputStream in = ClasspathText.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new FileNotFoundException(path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
