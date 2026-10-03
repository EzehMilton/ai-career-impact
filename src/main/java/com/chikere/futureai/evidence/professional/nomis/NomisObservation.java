package com.chikere.futureai.evidence.professional.nomis;

import java.math.BigDecimal;

record NomisObservation(String period, String occupationName, String geography,
        BigDecimal value, String unit) { }
