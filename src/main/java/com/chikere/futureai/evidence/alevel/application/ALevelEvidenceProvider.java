package com.chikere.futureai.evidence.alevel.application;

/**
 * The vendor-neutral A-level evidence port.
 *
 * <p>Evidence never affects assessment measures. It is the only place a factual claim about a
 * course's subject requirements may appear, and such a claim must always carry a source.</p>
 */
public interface ALevelEvidenceProvider {
    ALevelEvidenceBundle gather(ALevelEvidenceRequest request);

    EvidenceSource source();

    String providerIdentifier();
}
