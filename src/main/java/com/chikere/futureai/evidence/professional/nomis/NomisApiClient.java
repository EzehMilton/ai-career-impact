package com.chikere.futureai.evidence.professional.nomis;

interface NomisApiClient {
    NomisObservation latestUnitedKingdomEmployment(String soc2020Code);
}
