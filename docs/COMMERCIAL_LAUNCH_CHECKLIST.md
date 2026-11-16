# Commercial launch checklist

The public beta intentionally disables Stripe and uses one shared complimentary access code. Complete this checklist
before accepting any payment.

## Before accepting payment

- [ ] Set `STRIPE_ENABLED=true`.
- [ ] Configure the live Stripe secret key in `STRIPE_SECRET_KEY`.
- [ ] Configure all three live webhook secrets:
  - [ ] `STRIPE_WEBHOOK_SECRET` for Professional.
  - [ ] `UNIVERSITY_STRIPE_WEBHOOK_SECRET` for University.
  - [ ] `A_LEVEL_STRIPE_WEBHOOK_SECRET` for A-Level.
- [ ] Configure live Price IDs for all three products:
  - [ ] `PROFESSIONAL_REPORT_STRIPE_PRICE_ID`.
  - [ ] `UNIVERSITY_REPORT_STRIPE_PRICE_ID`.
  - [ ] `A_LEVEL_REPORT_STRIPE_PRICE_ID`.
- [ ] Verify every displayed price corresponds exactly to its live Stripe Price.
- [ ] Test checkout, signed webhook processing, entitlement activation, detailed-report generation and PDF download for
      Professional, University and A-Level.
- [ ] Set `COMPLIMENTARY_REPORT_ENABLED=false`.
- [ ] Remove `COMPLIMENTARY_REPORT_CODE` from production secrets.
- [ ] Keep `JUDGMENT_PROVIDER=openai`; Jev remains an explicitly selected contingency, not an automatic fallback.
- [ ] Keep production secure cookies, CSRF, request hardening, rate limiting, restricted logging and readiness checks
      enabled.
- [ ] Do not move an already activated `A_LEVEL_V3_PRESENTATION_CUTOVER` timestamp.

## Professional live evidence warning

**Do not enable `PROFESSIONAL_LIVE_EVIDENCE_ENABLED` for arbitrary commercial users while occupation classification
still supports only one operator-configured exact profession/SOC 2020 mapping.** The current mapping is not a general
profession classifier and is not production-ready for arbitrary professions.

Choose one of these commercial approaches:

1. Leave Professional live evidence disabled; or
2. first implement a reliable dynamic occupation-to-SOC-2020 classification capability, validate its mappings and
   failure behaviour on representative professions, and only then enable live evidence broadly.

If live evidence is deliberately enabled for a narrowly controlled exact profession, configure all three values and
verify that they describe the same mapping:

```text
PROFESSIONAL_EVIDENCE_PROFESSION=<exact submitted profession>
PROFESSIONAL_EVIDENCE_CANONICAL_OCCUPATION=<canonical occupation title>
PROFESSIONAL_EVIDENCE_SOC2020_CODE=<SOC 2020 code>
```
