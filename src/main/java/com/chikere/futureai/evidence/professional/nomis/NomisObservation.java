package com.chikere.futureai.evidence.professional.nomis;

import java.math.BigDecimal;

record NomisObservation(String period, String occupationName, BigDecimal value, String unit) { }
