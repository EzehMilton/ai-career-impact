# Professional Jev evaluation: professional-v1

> Live evaluation output. These observations are descriptive, not accuracy claims.

- Cases evaluated: 1
- Judgment schema: `professional-v1`
- Decision policy: `professional-decision-v1`
- Jev provenance: resolved model, request ID, and usage are not exposed by the current vendor-neutral judgment adapter.

## Software Developer (`routine_software_developer`)

**Purpose:** Probes implementation-heavy routine digital software work.

**Information sufficiency:** 0.840

| Atomic judgment | Value (0–4) | Nearest label | Confidence | Probability distribution |
|---|---:|---|---:|---|
| Task repeatability (`task_repeatability`) | 3.520 | Core work is highly repetitive, standardised and predictable. | 0.600 | 0=0.000, 1=0.000, 2=0.000, 3=0.480, 4=0.520 |
| Digital executability (`digital_executability`) | 4.000 | Almost all core work can be performed entirely through digital systems and information. | 1.000 | 0=0.000, 1=0.000, 2=0.000, 3=0.000, 4=1.000 |
| Human trust dependency (`human_trust_dependency`) | 0.480 | Human trust or interpersonal skill contributes very little to successful performance. | 0.600 | 0=0.520, 1=0.480, 2=0.000, 3=0.000, 4=0.000 |
| Physical-world dependency (`physical_world_dependency`) | 0.000 | The work has essentially no meaningful physical-world requirement. | 1.000 | 0=1.000, 1=0.000, 2=0.000, 3=0.000, 4=0.000 |
| Contextual complexity (`contextual_complexity`) | 0.070 | Work is highly structured and normally has clear inputs, rules and expected outputs. | 0.940 | 0=0.930, 1=0.070, 2=0.000, 3=0.000, 4=0.000 |
| Original judgment (`original_judgment`) | 0.110 | Work mainly applies established rules, templates or procedures. | 0.910 | 0=0.890, 1=0.110, 2=0.000, 3=0.000, 4=0.000 |
| Accountability and responsibility (`accountability_responsibility`) | 0.500 | The person has little independent responsibility for consequential outcomes. | 0.580 | 0=0.500, 1=0.500, 2=0.000, 3=0.000, 4=0.000 |
| AI complementarity (`ai_complementarity`) | 3.010 | AI could substantially increase productivity while important human contribution remains. | 0.880 | 0=0.000, 1=0.000, 2=0.060, 3=0.870, 4=0.070 |

### Professional decision

| Measure | Indicator (0–10) | Band | Confidence |
|---|---:|---|---:|
| AI Exposure | 9.446 | HIGH | 0.815 |
| Human Advantage | 0.577 | LOW | 0.807 |
| AI Complementarity | 7.525 | HIGH | 0.880 |
| Role Adaptability | 3.029 | LOW | 0.826 |

**Assessment confidence:** MODERATE  
**Assessment status:** READY_WITH_CAUTION

### Explainability

**Top exposure drivers:** `HIGH_DIGITAL_EXECUTABILITY` (signed effect +0.125), `HIGH_TASK_REPEATABILITY` (signed effect +0.095), `LOW_PHYSICAL_WORLD_DEPENDENCY` (signed effect +0.075)  
**Top protective factors:** None

## Context sensitivity comparisons

Differences show the second case minus the first. They are observations, not correctness labels.

### `routine_software_developer` vs `senior_software_architect`

Comparison not available because this run did not include both cases.

### `transactional_accountant` vs `advisory_management_accountant`

Comparison not available because this run did not include both cases.

### `graphic_production_designer` vs `creative_director`

Comparison not available because this run did not include both cases.

## Warnings

No configured evaluation hypotheses were triggered.

## Descriptive metrics

- Average information sufficiency: 0.840
- Average Score confidence: 0.814
- Lowest Score confidence: 0.580
- READY cases: 0
- READY_WITH_CAUTION cases: 1
- MORE_INFORMATION_REQUIRED cases: 0

### Indicator-band distributions

- AI Exposure: LOW=0, MODERATE=0, HIGH=1
- Human Advantage: LOW=1, MODERATE=0, HIGH=0
- AI Complementarity: LOW=0, MODERATE=0, HIGH=1
- Role Adaptability: LOW=1, MODERATE=0, HIGH=0
