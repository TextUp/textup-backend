package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.hibernate.*
import org.hibernate.transform.Transformers
import org.joda.time.*
import org.textup.type.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
final class UsageService {

    final String ACTIVITY_QUERY = """
        SUM(CASE WHEN i.class = "org.textup.RecordText" THEN 1 ELSE 0 END) AS numTexts,
        SUM(CASE WHEN i.class = "org.textup.RecordText" THEN rir.num_billable ELSE 0 END) AS numBillableSegments,
        SUM(CASE WHEN i.class = "org.textup.RecordCall" THEN 1 ELSE 0 END) AS numCalls,
        (SUM(CASE WHEN i.class = "org.textup.RecordCall" THEN rir.num_billable ELSE 0 END) / 60) AS numBillableMinutes
    """
    final String ACTIVE_PHONES_QUERY = "COUNT(DISTINCT(p.id)) AS numActivePhones"
    final String ACTIVITY_QUERY_SOURCE = """
        FROM record_item AS i
        JOIN record_item_receipt AS rir ON rir.item_id = i.id
        JOIN contact AS c ON c.record_id = i.record_id
        JOIN phone AS p ON c.phone_id = p.id
    """

    SessionFactory sessionFactory

    public static class ActivityRecord {
        BigInteger ownerId
        String monthString
        BigInteger numActivePhones = 0
        BigDecimal numTexts = 0
        BigDecimal numBillableSegments = 0
        BigDecimal numCalls = 0
        BigDecimal numBillableMinutes = 0

        void setMonthString(String queryMonth) {
            this.monthString = UsageUtils.queryMonthToMonthString(queryMonth)
        }
        void setMonthStringDirectly(String monthString) {
            this.monthString = monthString
        }
    }

    public static class HasActivity {
        BigInteger id
        UsageService.ActivityRecord activity = new ActivityRecord()
    }

    public static class Organization extends HasActivity {
        String name
        BigInteger totalNumPhones = 0
    }

    public static class Staff extends HasActivity {
        String name
        String username
        String email
        String number
        PhoneNumber getPhoneNumber() { new PhoneNumber(number: number) }
    }

    public static class Team extends HasActivity {
        String name
        BigInteger numStaff = 0
        String number
        PhoneNumber getPhoneNumber() { new PhoneNumber(number: number) }
    }

    List<UsageService.Organization> getOverallPhoneActivity(DateTime dt, PhoneOwnershipType type) {
        if (!dt || !type) {
            return []
        }
        UsageUtils.associateActivity(getAllOrgs(dt, type), getAllActivity(dt, type))
    }

    List<UsageService.Staff> getStaffPhoneActivity(DateTime dt, Long orgId) {
        if (!dt || !orgId) {
            return []
        }
        UsageUtils.associateActivity(getStaffForOrg(dt, orgId),
            getActivityForOrg(dt, orgId, PhoneOwnershipType.INDIVIDUAL))
    }

    List<UsageService.Team> getTeamPhoneActivity(DateTime dt, Long orgId) {
        if (!dt || !orgId) {
            return []
        }
        UsageUtils.associateActivity(getTeamsForOrg(dt, orgId),
            getActivityForOrg(dt, orgId, PhoneOwnershipType.GROUP))
    }

    List<UsageService.ActivityRecord> getActivity(PhoneOwnershipType type) {
        UsageUtils.ensureMonths(getActivityOverTime(type))
    }

    List<UsageService.ActivityRecord> getActivityForOrg(PhoneOwnershipType type, Long orgId) {
        UsageUtils.ensureMonths(getActivityOverTimeForOrg(type, orgId))
    }

    List<UsageService.ActivityRecord> getActivityForNumber(String number) {
        UsageUtils.ensureMonths(getActivityOverTimeForNumber(number))
    }

    // Details
    // -------

    protected List<UsageService.Organization> getAllOrgs(DateTime dt, PhoneOwnershipType type) {
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
                setResultTransformer(Transformers.aliasToBean(UsageService.Organization.class))
                setString("type", type.toString())
                setInteger("year", dt.year)
                setInteger("month", dt.monthOfYear)
                list()
            }
    }

    protected List<UsageService.Staff> getStaffForOrg(DateTime dt, Long orgId) {
        sessionFactory.currentSession
            .createSQLQuery("""
                SELECT m.id as `id`,
                    m.name AS `name`,
                    m.username AS username,
                    m.email AS email,
                    p.number_as_string AS number
                FROM phone AS p
                JOIN phone_ownership AS o ON p.owner_id = o.id
                JOIN staff AS m ON m.id = o.owner_id
                WHERE (EXTRACT(YEAR FROM p.when_created) < :year
                        OR (EXTRACT(YEAR FROM p.when_created) = :year AND EXTRACT(MONTH FROM p.when_created) <= :month))
                    AND o.type = "INDIVIDUAL"
                    AND m.org_id = :orgId
                    AND p.number_as_string IS NOT NULL
                GROUP BY p.number_as_string
                ORDER BY m.name ASC;
            """).with {
                setResultTransformer(Transformers.aliasToBean(UsageService.Staff.class))
                setInteger("year", dt.year)
                setInteger("month", dt.monthOfYear)
                setLong("orgId", orgId)
                list()
            }
    }

    protected List<UsageService.Team> getTeamsForOrg(DateTime dt, Long orgId) {
        sessionFactory.currentSession
            .createSQLQuery("""
                SELECT m.id as `id`,
                    m.name AS `name`,
                    COUNT(DISTINCT(ts.staff_id)) AS numStaff,
                    p.number_as_string AS number
                FROM phone AS p
                JOIN phone_ownership AS o ON p.owner_id = o.id
                JOIN team AS m ON m.id = o.owner_id
                JOIN team_staff AS ts ON ts.team_members_id = m.id
                WHERE (EXTRACT(YEAR FROM p.when_created) < :year
                        OR (EXTRACT(YEAR FROM p.when_created) = :year AND EXTRACT(MONTH FROM p.when_created) <= :month))
                    AND o.type = "GROUP"
                    AND m.org_id = :orgId
                    AND p.number_as_string IS NOT NULL
                GROUP BY p.number_as_string
                ORDER BY m.name ASC;
            """).with {
                setResultTransformer(Transformers.aliasToBean(UsageService.Team.class))
                setInteger("year", dt.year)
                setInteger("month", dt.monthOfYear)
                setLong("orgId", orgId)
                list()
            }
    }

    // Point-in-time activity
    // ----------------------

    protected List<UsageService.ActivityRecord> getAllActivity(DateTime dt, PhoneOwnershipType type) {
        sessionFactory.currentSession
            .createSQLQuery("""
                SELECT org.id as ownerId,
                    ${ACTIVE_PHONES_QUERY},
                    ${ACTIVITY_QUERY}
                ${ACTIVITY_QUERY_SOURCE}
                JOIN phone_ownership AS o ON p.owner_id = o.id
                JOIN ${UsageUtils.getTableName(type)} AS m ON m.id = o.owner_id
                JOIN organization AS org ON m.org_id = org.id
                WHERE EXTRACT(YEAR FROM i.when_created) = :year
                    AND EXTRACT(MONTH FROM i.when_created) = :month
                    AND o.type = :type
                GROUP BY org.id
                ORDER BY COUNT(DISTINCT(p.id)) DESC;
            """.toString()).with {
                setResultTransformer(Transformers.aliasToBean(UsageService.ActivityRecord.class))
                setString("type", type.toString())
                setInteger("year", dt.year)
                setInteger("month", dt.monthOfYear)
                list()
            }
    }

    protected List<UsageService.ActivityRecord> getActivityForOrg(DateTime dt, Long orgId, PhoneOwnershipType type) {
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
                GROUP BY p.number_as_string
                ORDER BY m.name ASC;
            """.toString()).with {
                setResultTransformer(Transformers.aliasToBean(UsageService.ActivityRecord.class))
                setString("type", type.toString())
                setInteger("year", dt.year)
                setInteger("month", dt.monthOfYear)
                setLong("orgId", orgId)
                list()
            }
    }

    // Activity over time
    // ------------------

    protected List<UsageService.ActivityRecord> getActivityOverTime(PhoneOwnershipType type) {
        sessionFactory.currentSession
            .createSQLQuery("""
                SELECT LEFT(i.when_created, 7) AS monthString,
                    ${ACTIVE_PHONES_QUERY},
                    ${ACTIVITY_QUERY}
                ${ACTIVITY_QUERY_SOURCE}
                JOIN phone_ownership AS o ON p.owner_id = o.id
                JOIN ${UsageUtils.getTableName(type)} AS m ON m.id = o.owner_id
                JOIN organization AS org ON m.org_id = org.id
                WHERE o.type = :type
                GROUP BY LEFT(i.when_created, 7)
                ORDER BY LEFT(i.when_created, 7) ASC;
            """.toString()).with {
                setResultTransformer(Transformers.aliasToBean(UsageService.ActivityRecord.class))
                setString("type", type.toString())
                list()
            }
    }

    protected List<UsageService.ActivityRecord> getActivityOverTimeForOrg(PhoneOwnershipType type,
        Long orgId) {

        sessionFactory.currentSession
            .createSQLQuery("""
                SELECT LEFT(i.when_created, 7) AS monthString,
                    ${ACTIVE_PHONES_QUERY},
                    ${ACTIVITY_QUERY}
                ${ACTIVITY_QUERY_SOURCE}
                JOIN phone_ownership AS o ON p.owner_id = o.id
                JOIN ${UsageUtils.getTableName(type)} AS m ON m.id = o.owner_id
                WHERE o.type = :type
                    AND m.org_id = :orgId
                GROUP BY LEFT(i.when_created, 7)
                ORDER BY LEFT(i.when_created, 7) ASC;
            """.toString()).with {
                setResultTransformer(Transformers.aliasToBean(UsageService.ActivityRecord.class))
                setString("type", type.toString())
                setLong("orgId", orgId)
                list()
            }
    }

    protected List<UsageService.ActivityRecord> getActivityOverTimeForNumber(String number) {
        sessionFactory.currentSession
            .createSQLQuery("""
                SELECT LEFT(i.when_created, 7) AS monthString,
                    ${ACTIVE_PHONES_QUERY},
                    ${ACTIVITY_QUERY}
                FROM record_item AS i
                JOIN record_item_receipt AS rir ON rir.item_id = i.id
                JOIN contact AS c ON c.record_id = i.record_id
                JOIN phone AS p ON c.phone_id = p.id
                WHERE p.number_as_string = :number
                GROUP BY LEFT(i.when_created, 7)
                ORDER BY LEFT(i.when_created, 7) ASC;
            """.toString()).with {
                setResultTransformer(Transformers.aliasToBean(UsageService.ActivityRecord.class))
                setString("number", number)
                list()
            }
    }
}
