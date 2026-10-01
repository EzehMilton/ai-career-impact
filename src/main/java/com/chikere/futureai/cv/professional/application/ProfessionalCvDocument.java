package com.chikere.futureai.cv.professional.application;

import java.util.Arrays;
import java.util.Objects;

public record ProfessionalCvDocument(
        String originalFilename,
        ProfessionalCvDocumentType type,
        byte[] bytes) {

    public ProfessionalCvDocument {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("originalFilename must not be blank");
        }
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(bytes, "bytes must not be null");
        if (bytes.length == 0) {
            throw new IllegalArgumentException("bytes must not be empty");
        }
        bytes = Arrays.copyOf(bytes, bytes.length);
    }

    @Override
    public byte[] bytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }

    public long size() {
        return bytes.length;
    }
}
