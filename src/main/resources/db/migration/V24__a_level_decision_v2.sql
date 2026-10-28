-- Stage A2: the a-level-decision-v2 subject profile of new A-level assessments. Historical a-level-decision-v1 rows
-- are untouched: a v1 assessment keeps its six measures in a_level_assessment_measure and its strengths and
-- development factors in a_level_assessment_factor, and has no rows here. A v2 assessment has no rows there.

-- The four scored dimensions of an assessment decided by a-level-decision-v2 (Options Kept Open, the two
-- Capability Profile sides and Learning Alongside AI), with the confidence each was decided at.
create table a_level_assessment_profile_measure (
    assessment_id uuid not null references a_level_assessment(id) on delete cascade,
    measure varchar(64) not null,
    indicator double precision not null,
    band varchar(16) not null,
    confidence double precision not null,
    lower_confidence boolean not null,
    primary key (assessment_id, measure),
    constraint a_level_profile_band check (band in ('LOW', 'MODERATE', 'HIGH'))
);

-- Every signed contribution of each v2 measure, in its stored order; drivers and limits are flagged subsets, so a
-- later policy change can never re-select them.
create table a_level_assessment_profile_factor (
    assessment_id uuid not null references a_level_assessment(id) on delete cascade,
    measure varchar(64) not null,
    position integer not null,
    dimension_id varchar(100) not null,
    score double precision not null,
    weight double precision not null,
    effect double precision not null,
    driver_position integer,
    limiting_position integer,
    primary key (assessment_id, measure, position),
    constraint a_level_profile_factor_role check (driver_position is null or limiting_position is null)
);
