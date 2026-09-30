package com.chikere.futureai.evidence.professional.application;

public interface ProfessionalEvidenceProvider {
    ProfessionalEvidenceBundle gather(ProfessionalEvidenceRequest request);
    EvidenceSource source();
    String providerIdentifier();
}
