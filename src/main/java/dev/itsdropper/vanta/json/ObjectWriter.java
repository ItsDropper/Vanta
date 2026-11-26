package dev.itsdropper.vanta.json;

import java.io.File;
import java.io.IOException;

public final class ObjectWriter {
    private final ObjectMapper mapper;
    ObjectWriter(ObjectMapper mapper) { this.mapper = mapper; }
    public void writeValue(File file, Object value) throws IOException { mapper.writeValue(file, value); }
}
