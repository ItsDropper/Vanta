package dev.itsdropper.vanta.json;

import java.io.IOException;

/** Checked exception raised when Vanta JSON input is malformed or violates a configured schema. */
public final class JsonProcessingException extends IOException {
    public JsonProcessingException(String message) { super(message); }
    public JsonProcessingException(String message, Throwable cause) { super(message, cause); }
    public String getOriginalMessage() { return getMessage(); }
}
