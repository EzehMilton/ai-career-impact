package com.chikere.futureai.evidence.university.application;

/** The vendor-neutral university evidence port. Evidence never affects assessment scores. */
public interface UniversityEvidenceProvider {
    UniversityEvidenceBundle gather(UniversityEvidenceRequest request);

    EvidenceSource source();

    String providerIdentifier();
}
