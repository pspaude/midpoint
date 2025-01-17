/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 *
 * @formatter:off because of terribly unreliable IDEA reformat for SQL
 */

/*
drop table xoid;

create table xoid as
select generate_series(1, 100) as id, gen_random_uuid() as oid;
*/

create table xoid (id bigint primary key, oid TEXT);
insert into xoid (select generate_series(1, 100) as id, md5(random()::TEXT) as oid);

select * from xoid;
/* "statistical" check that numbers from 1 to 10 are selected (including), no 0
select min(a), max(a)  --                |    |
from (select width_bucket(random(), 0,   1,   10) as a from generate_series(1, 20000)) q;
*/

select width_bucket(random(), 0, 1, 100);
select * from width_bucket(random(), 0, 1, 100);
select pg_typeof(width_bucket(random(), 0, 1, 100));

select * from xoid
    where xoid.id = width_bucket(random(), 0, 1, 100);

select * from xoid
    where xoid.id = (select width_bucket(random(), 0, 1, 100));

select * from
     (select b, width_bucket(random(), 0, 1, 100) id from generate_series(1,10) b) a
    left join xoid on xoid.id = a.id;

-- region OLD REPO experiments

-- This finds all the orgs for the user
-- in m_reference owner = child, target = parent
EXPLAIN (ANALYZE, VERBOSE, BUFFERS)
with recursive org_h (
    parent,
    child
) as (
    select r.targetoid, r.ownerOid from m_reference as r
        where r.reference_type = 0
            -- this condition makes it super fast, the same out of CTE is much slower
            and r.ownerOid = 'u7:21200-0...-....-....-............'
    union
    select par.targetoid, chi.child
        from m_reference as par, org_h as chi
        where par.reference_type = 0
            and par.ownerOid = chi.parent
)
select distinct parent from org_h
-- select * from org_h
--     where child = 'u7:21200-0...-....-....-............'
;

-- endregion

select * from m_ref_object_parent_org;
select * from m_object;
select * from m_uri;

set jit = off;

-- generated by Querydsl
EXPLAIN (ANALYZE, VERBOSE, BUFFERS)
with recursive orgc (parent, child) as not materialized (
    (select refpo.targetOid, refpo.ownerOid from m_ref_object_parent_org refpo)
    union
    (select refpo.targetOid, orgc.child from m_ref_object_parent_org refpo, orgc orgc where refpo.ownerOid = orgc.parent)
)
select po.nameOrig, parent, child,
       co.nameOrig,
       * from orgc
    join m_object po on po.oid = parent
    join m_object co on co.oid = child
;
select orgc.parent, orgc.child from orgc orgc
where orgc.child = gen_random_uuid() limit 10
;

EXPLAIN (ANALYZE, VERBOSE, BUFFERS)
select count(*) from m_org_closure where ancestor_oid = '1047bd4e-79ff-466c-802f-c42fc7e3ebaf';
select * from m_ref_object_parent_org;

CREATE OR REPLACE FUNCTION m_org_clsr(ancestorOid UUID)
    RETURNS TABLE (
        ancestor_oid UUID, -- ref.targetoid
        descendant_oid UUID --ref.ownerOid
    )
    LANGUAGE plpgsql
AS $$
DECLARE
    flag_val text;
BEGIN
    -- No lock here, if the view is OK, we don't want any locking at all.
    SELECT value INTO flag_val FROM m_global_metadata WHERE name = 'orgClosureRefreshNeeded';
    IF flag_val = 'true' THEN
        CALL m_refresh_org_closure();
    END IF;

    RETURN QUERY SELECT * FROM m_org_closure_internal oci where oci.ancestor_oid = ancestorOid;
END $$;

select count(*) from m_org_closure_internal;

select * from m_user
order by oid;

-- using function m_org_clsr(ancestorOid): 1000 ms for oids, 1400 ms for full rows
-- using m_org_closure with rule: 23 s! no noticeable difference between oid/full select
select oid from m_user u
where u.nameNorm like '%45'
    and exists (select 1 from
        m_ref_object_parent_org pref
        join
--         m_org_closure oc on pref.targetoid = oc.descendant_oid
        m_org_clsr('4e84dcec-eff6-4f1c-9bad-929e98dea3fa') oc on pref.targetoid = oc.descendant_oid
            and pref.ownerOid = u.oid
--         where oc.ancestor_oid = '4e84dcec-eff6-4f1c-9bad-929e98dea3fa'
);

-- using m_org_closure_internal directly 150 ms for just OIDs (600 ms full rows)
select oid from m_user u
where u.nameNorm like '%45'
    and exists (select 1 from
        m_ref_object_parent_org pref
        join
        m_org_closure_internal oc on pref.targetoid = oc.descendant_oid
            and pref.ownerOid = u.oid
        where oc.ancestor_oid = '4e84dcec-eff6-4f1c-9bad-929e98dea3fa'
);

WITH RECURSIVE org_h (
    ancestor_oid, -- ref.targetoid
    descendant_oid --ref.ownerOid
) AS (
    -- gather all organizations with parents
    SELECT r.targetoid, r.ownerOid
    FROM m_ref_object_parent_org r
    WHERE r.ownerType = 'ORG'
    UNION
    -- generate their parents
    SELECT par.targetoid, chi.descendant_oid -- leaving original child there generates closure
    FROM m_ref_object_parent_org as par, org_h as chi
    WHERE par.ownerOid = chi.ancestor_oid
),
pref as (
    select pref.* from m_ref_object_parent_org pref
        join org_h oc on pref.targetoid = oc.descendant_oid
    where oc.ancestor_oid = '4e84dcec-eff6-4f1c-9bad-929e98dea3fa'
)
select oid from m_user u
where
      u.nameNorm like '%45' and
      exists (select 1 from pref where pref.ownerOid = u.oid)
;
-- select count(*) from org_h;
-- select * from org_h;
;
select oid from m_user u
where u.nameNorm like '%45'
    and exists (select 1 from
        m_ref_object_parent_org pref
        join
        org_h oc on pref.targetoid = oc.descendant_oid
            and pref.ownerOid = u.oid
        where oc.ancestor_oid = '4e84dcec-eff6-4f1c-9bad-929e98dea3fa'
);

select * from m_ref_object_parent_org
    where ownerOid = '0f9badc4-3fc2-4977-aa0a-f08c3576383d';

select * from m_org_closure oc
    where oc.descendant_oid = '6e732607-609f-46de-9747-1675868dc227';

select * from m_org_closure oc
    where oc.ancestor_oid = '4e84dcec-eff6-4f1c-9bad-929e98dea3fa'; -- parent
--     where oc.ancestor_oid = '29d933cc-a26c-49c7-afb5-a7b0e39121f2'; -- child
--     where oc.ancestor_oid = '5e3ff03b-7366-4ed5-9d8c-7dc8186b70c6'; -- next child

select * from m_org_closure oc
    where oc.descendant_oid = '29d933cc-a26c-49c7-afb5-a7b0e39121f2';

select * from m_global_metadata
;

refresh materialized view m_org_closure;

select * from m_org;
select count(*) from m_org;
select count(*) from m_org_closure;
select count(*) from m_user;

-- Perf test adding orgs:
-- trigger with refresh: orgs/closure: 15125/72892 29m31s, ~8.5 orgs/s (most late addObject took ~220ms)
-- empty trigger: orgs/closure: 14052/67735 (after manual refresh taking ~230 ms) 31s, ~450 orgs/s
-- trigger with mark: orgs/closure: 14573/70225 (after m_refresh_org_closure ~300 ms) 32s, ~455 orgs/s
-- trigger with mark: orgs/closure: 59711/291099 (after refresh ~1.9s), 2m13s, ~450 orgs/s

select * from m_org o
    where not exists (select 1 from m_ref_object_parent_org po where po.ownerOid = o.oid);

select * from m_org_closure;

select * FROM m_global_metadata;

CALL m_refresh_org_closure(true);
