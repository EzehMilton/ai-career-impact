package com.chikere.futureai.pdf.alevel.application;

import java.util.Objects;

public record RenderedALevelReportPdf(byte[] bytes) {
    public RenderedALevelReportPdf {
        bytes = Objects.requireNonNull(bytes).clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
