package org.textup.util

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.hibernate.*
import org.hibernate.transform.Transformers
import org.joda.time.*
import org.textup.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*

// NOTE: for speed, we wrote the queries in raw SQL. For compatibility between MySQL (prod)
// and H2 (testing),
//
// (1) do not use backticks to enclose column aliases, AND make sure to append
//      ";DATABASE_TO_UPPER=FALSE" to the H2 URL do it does not auto-capitalize
// (2) use single quotes to enclose string literals
// (3) denominators in division operations should be written as floats or else H2 db will
//      interpret the division result as an integer, truncating the decimal places
//      Specifically, dividing by 60 must be written as "60.0"
// (4) GROUP BY clause needs to have `when_created` in it because we run an aggregation function on it
//      MySQL permits not doing so but other stricter dbs (like H2) require it
//      see: https://stackoverflow.com/questions/31596497/h2-db-column-must-be-in-group-by-list

@GrailsTypeChecked
@Transactional
class UsageService {

    private static final String ACTIVITY_QUERY = """
        LEFT(i.when_created, 7) AS monthString,

        SUM(CASE WHEN i.num_notified IS NOT NULL THEN i.num_notified ELSE 0 END) AS numNotificationTexts,

        SUM(CASE WHEN i.class = 'org.textup.RecordText' AND i.outgoing = TRUE THEN 1 ELSE 0 END) AS numOutgoingTexts,
        SUM(CASE WHEN i.class = 'org.textup.RecordText' AND i.outgoing = TRUE THEN rir.num_segments ELSE 0 END) AS numOutgoingSegments,
        SUM(CASE WHEN i.class = 'org.textup.RecordText' AND i.outgoing = FALSE THEN 1 ELSE 0 END) AS numIncomingTexts,
        SUM(CASE WHEN i.class = 'org.textup.RecordText' AND i.outgoing = FALSE THEN rir.num_segments ELSE 0 END) AS numIncomingSegments,

        SUM(CASE WHEN i.voicemail_in_seconds IS NOT NULL THEN i.voicemail_in_seconds / 60.0 ELSE 0 END) AS numVoicemailMinutes,
        SUM(CASE WHEN i.voicemail_in_seconds IS NOT NULL THEN CEIL(i.voicemail_in_seconds / 60.0) ELSE 0 END) AS numBillableVoicemailMinutes,

        SUM(CASE WHEN i.class = 'org.textup.RecordCall' AND i.outgoing = TRUE THEN 1 ELSE 0 END) AS numOutgoingCalls,
        SUM(CASE WHEN i.class = 'org.textup.RecordCall' AND i.outgoing = TRUE THEN rir.num_minutes ELSE 0 END) AS numOutgoingMinutes,
        SUM(CASE WHEN i.class = 'org.textup.RecordCall' AND i.outgoing = TRUE THEN rir.ceil_num_minutes ELSE 0 END) AS numOutgoingBillableMinutes,
        SUM(CASE WHEN i.class = 'org.textup.RecordCall' AND i.outgoing = FALSE THEN 1 ELSE 0 END) AS numIncomingCalls,
        SUM(CASE WHEN i.class = 'org.textup.RecordCall' AND i.outgoing = FALSE THEN rir.num_minutes ELSE 0 END) AS numIncomingMinutes,
        SUM(CASE WHEN i.class = 'org.textup.RecordCall' AND i.outgoing = FALSE THEN rir.ceil_num_minutes ELSE 0 END) AS numIncomingBillableMinutes
    """
    private static final String ACTIVE_PHONES_QUERY = "COUNT(DISTINCT(p.id)) AS numActivePhones"
    private static final String ACTIVITY_QUERY_SOURCE = """
        FROM record_item AS i
        JOIN (SELECT item_id,
                (CASE WHEN num_billable IS NOT NULL THEN num_billable ELSE 1 END) AS num_segments,
                (CASE WHEN num_billable IS NOT NULL THEN num_billable / 60.0 ELSE 1 END) AS num_minutes,
                (CASE WHEN num_billable IS NOT NULL THEN CEIL(num_billable / 60.0) ELSE 1 END) AS ceil_num_minutes
            FROM record_item_receipt) AS rir ON rir.item_id = i.id
        JOIN phone_record AS pr ON pr.record_id = i.record_id
        JOIN phone AS p ON pr.phone_id = p.id
    """
    private static final String ACTIVITY_QUERY_CONDITION = "AND pr.class = 'org.textup.IndividualPhoneRecord'"

    SessionFactory sessionFactory

    List<ActivityEntity.Organization> getOverallPhoneActivity(DateTime dt, PhoneOwnershipType type) {
        if (!dt || !type) {
            return []
        }
        UsageUtils.associateActivityForMonth(dt, getAllOrgs(dt, type), getAllActivity(dt, type))
    }

    List<ActivityEntity.Staff> getStaffPhoneActivity(DateTime dt, Long orgId) {
        if (!dt || !orgId) {
            return []
        }
        UsageUtils.associateActivityForMonth(dt, getStaffForOrg(dt, orgId),
            getActivityForOrgForMonth(dt, orgId, PhoneOwnershipType.INDIVIDUAL))
    }

    List<ActivityEntity.Team> getTeamPhoneActivity(DateTime dt, Long orgId) {
        if (!dt || !orgId) {
            return []
        }
        UsageUtils.associateActivityForMonth(dt, getTeamsForOrg(dt, orgId),
            getActivityForOrgForMonth(dt, orgId, PhoneOwnershipType.GROUP))
    }

    List<ActivityRecord> getActivity(PhoneOwnershipType type) {
        UsageUtils.ensureMonths(getActivityOverTime(type))
    }

    List<ActivityRecord> getActivityForOrg(PhoneOwnershipType type, Long orgId) {
        UsageUtils.ensureMonths(getActivityOverTimeForOrg(type, orgId))
    }

    List<ActivityRecord> getActivityForPhoneId(Long phoneId) {
        UsageUtils.ensureMonths(getActivityOverTimeForPhoneId(phoneId))
    }

    // Details
    // -------

    protected List<ActivityEntity.Organization> getAllOrgs(DateTime dt, PhoneOwnershipType type) {
        sessionFactory.currentSession
            .createSQLQuery("""
                SELECT org.id AS id,
                    org.name AS name,
                    COUNT(DISTINCT(p.number_as_string)) AS totalNumPhones
                FROM ${UsageUtils.getTableName(type)} AS m
                JOIN phone_ownership AS po ON po.owner_id = m.id
                JOIN phone AS p ON p.id = po.phone_id
                JOIN organization AS org ON m.org_id = org.id
                WHERE po.type = :type
                    AND p.number_as_string IS NOT NULL
                    AND (EXTRACT(YEAR FROM p.when_created) < :year
                        OR (EXTRACT(YEAR FROM p.when_created) = :year AND EXTRACT(MONTH FROM p.when_created) <= :month))
                GROUP BY org.id
                ORDER BY count(*) DESC;
            """.toString()).with {
                setResultTransformer(Transformers.aliasToBean(ActivityEntity.Organization.class))
                setString("type", type.toString())
                setInteger("year", dt.year)
                setInteger("month", dt.monthOfYear)
                list()
            }
    }

    protected List<ActivityEntity.Staff> getStaffForOrg(DateTime dt, Long orgId) {
        sessionFactory.currentSession
            .createSQLQuery("""
                SELECT m.id AS id,
                    m.name AS name,
                    m.username AS username,
                    m.email AS email,
                    p.id AS phoneId
                FROM phone AS p
                JOIN phone_ownership AS o ON p.owner_id = o.id
                JOIN staff AS m ON m.id = o.owner_id
                WHERE (EXTRACT(YEAR FROM p.when_created) < :year
                        OR (EXTRACT(YEAR FROM p.when_created) = :year AND EXTRACT(MONTH FROM p.when_created) <= :month))
                    AND o.type = 'INDIVIDUAL'
                    AND m.org_id = :orgId
                    AND p.number_as_string IS NOT NULL
                GROUP BY p.number_as_string
                ORDER BY m.name ASC;
            """).with {
                setResultTransformer(Transformers.aliasToBean(ActivityEntity.Staff.class))
                setInteger("year", dt.year)
                setInteger("month", dt.monthOfYear)
                setLong("orgId", orgId)
                list()
            }
    }

    protected List<ActivityEntity.Team> getTeamsForOrg(DateTime dt, Long orgId) {
        sessionFactory.currentSession
            .createSQLQuery("""
                SELECT m.id AS id,
                    m.name AS name,
                    COUNT(DISTINCT(ts.staff_id)) AS numStaff,
                    p.id AS phoneId
                FROM phone AS p
                JOIN phone_ownership AS o ON p.owner_id = o.id
                JOIN team AS m ON m.id = o.owner_id
                JOIN team_staff AS ts ON ts.team_members_id = m.id
                WHERE (EXTRACT(YEAR FROM p.when_created) < :year
                        OR (EXTRACT(YEAR FROM p.when_created) = :year AND EXTRACT(MONTH FROM p.when_created) <= :month))
                    AND o.type = 'GROUP'
                    AND m.org_id = :orgId
                    AND p.number_as_string IS NOT NULL
                GROUP BY p.number_as_string
                ORDER BY m.name ASC;
            """).with {
                setResultTransformer(Transformers.aliasToBean(ActivityEntity.Team.class))
                setInteger("year", dt.year)
                setInteger("month", dt.monthOfYear)
                setLong("orgId", orgId)
                list()
            }
    }

    // Point-in-time activity
    // ----------------------

    protected List<ActivityRecord> getAllActivity(DateTime dt, PhoneOwnershipType type) {
        sessionFactory.currentSession
            .createSQLQuery("""
                SELECT org.id AS ownerId,
                    ${ACTIVE_PHONES_QUERY},
                    ${ACTIVITY_QUERY}
                ${ACTIVITY_QUERY_SOURCE}
                JOIN phone_ownership AS o ON p.owner_id = o.id
                JOIN ${UsageUtils.getTableName(type)} AS m ON m.id = o.owner_id
                JOIN organization AS org ON m.org_id = org.id
                WHERE EXTRACT(YEAR FROM i.when_created) = :year
                    AND EXTRACT(MONTH FROM i.when_created) = :month
                    AND o.type = :type
                    ${ACTIVITY_QUERY_CONDITION}
                GROUP BY org.id, LEFT(i.when_created, 7)
                ORDER BY COUNT(DISTINCT(p.id)) DESC;
            """.toString()).with {
                setResultTransformer(Transformers.aliasToBean(ActivityRecord.class))
                setString("type", type.toString())
                setInteger("year", dt.year)
                setInteger("month", dt.monthOfYear)
                list()
            }
    }

    protected List<ActivityRecord> getActivityForOrgForMonth(DateTime dt, Long orgId, PhoneOwnershipType type) {
        sessionFactory.currentSession
            .createSQLQuery("""
                SELECT m.id AS ownerId,
                    1 as numActivePhones,
                    ${ACTIVITY_QUERY}
                ${ACTIVITY_QUERY_SOURCE}
                JOIN phone_ownership AS o ON p.owner_id = o.id
                JOIN ${UsageUtils.getTableName(type)} AS m ON m.id = o.owner_id
                WHERE EXTRACT(YEAR FROM i.when_created) = :year
                    AND EXTRACT(MONTH FROM i.when_created) = :month
                    AND o.type = :type
                    AND m.org_id = :orgId
                    AND p.number_as_string IS NOT NULL
                    ${ACTIVITY_QUERY_CONDITION}
                GROUP BY p.number_as_string, LEFT(i.when_created, 7)
                ORDER BY m.name ASC;
            """.toString()).with {
                setResultTransformer(Transformers.aliasToBean(ActivityRecord.class))
                setString("type", type.toString())
                setInteger("year", dt.year)
                setInteger("month", dt.monthOfYear)
                setLong("orgId", orgId)
                list()
            }
    }

    // Activity over time
    // ------------------

    protected List<ActivityRecord> getActivityOverTime(PhoneOwnershipType type) {
        sessionFactory.currentSession
            .createSQLQuery("""
                SELECT ${ACTIVE_PHONES_QUERY},
                    ${ACTIVITY_QUERY}
                ${ACTIVITY_QUERY_SOURCE}
                JOIN phone_ownership AS o ON p.owner_id = o.id
                JOIN ${UsageUtils.getTableName(type)} AS m ON m.id = o.owner_id
                JOIN organization AS org ON m.org_id = org.id
                WHERE o.type = :type
                    ${ACTIVITY_QUERY_CONDITION}
                GROUP BY LEFT(i.when_created, 7)
                ORDER BY LEFT(i.when_created, 7) ASC;
            """.toString()).with {
                setResultTransformer(Transformers.aliasToBean(ActivityRecord.class))
                setString("type", type.toString())
                list()
            }
    }

    protected List<ActivityRecord> getActivityOverTimeForOrg(PhoneOwnershipType type,
        Long orgId) {

        sessionFactory.currentSession
            .createSQLQuery("""
                SELECT ${ACTIVE_PHONES_QUERY},
                    ${ACTIVITY_QUERY}
                ${ACTIVITY_QUERY_SOURCE}
                JOIN phone_ownership AS o ON p.owner_id = o.id
                JOIN ${UsageUtils.getTableName(type)} AS m ON m.id = o.owner_id
                WHERE o.type = :type
                    AND m.org_id = :orgId
                    ${ACTIVITY_QUERY_CONDITION}
                GROUP BY LEFT(i.when_created, 7)
                ORDER BY LEFT(i.when_created, 7) ASC;
            """.toString()).with {
                setResultTransformer(Transformers.aliasToBean(ActivityRecord.class))
                setString("type", type.toString())
                setLong("orgId", orgId)
                list()
            }
    }

    protected List<ActivityRecord> getActivityOverTimeForPhoneId(Long phoneId) {
        sessionFactory.currentSession
            .createSQLQuery("""
                SELECT ${ACTIVE_PHONES_QUERY},
                    ${ACTIVITY_QUERY}
                ${ACTIVITY_QUERY_SOURCE}
                WHERE p.id = :phoneId
                    ${ACTIVITY_QUERY_CONDITION}
                GROUP BY LEFT(i.when_created, 7)
                ORDER BY LEFT(i.when_created, 7) ASC;
            """.toString()).with {
                setResultTransformer(Transformers.aliasToBean(ActivityRecord.class))
                setLong("phoneId", phoneId)
                list()
            }
    }
}
