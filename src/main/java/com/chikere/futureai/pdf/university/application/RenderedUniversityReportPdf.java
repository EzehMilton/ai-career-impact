package com.chikere.futureai.pdf.university.application;

import java.util.Objects;

public record RenderedUniversityReportPdf(byte[] bytes) {
    public RenderedUniversityReportPdf {
        bytes = Objects.requireNonNull(bytes).clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
