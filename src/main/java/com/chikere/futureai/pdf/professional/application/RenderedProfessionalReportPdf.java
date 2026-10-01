package com.chikere.futureai.pdf.professional.application;

import java.util.Objects;

public record RenderedProfessionalReportPdf(byte[] bytes) {
    public RenderedProfessionalReportPdf {
        bytes = Objects.requireNonNull(bytes).clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
