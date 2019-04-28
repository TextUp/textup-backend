import groovy.sql.*
import org.textup.*

databaseChangeLog = {

    changeSet(author: "ericbai (generated)", id: "1551375338796-12") {
        addColumn(tableName: "schedule") {
            column(name: "manual", type: "bit", valueBoolean: true) {
                constraints(nullable: "false")
            }
        }
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-13") {
        addColumn(tableName: "schedule") {
            column(name: "manual_is_available", type: "bit", valueBoolean: true) {
                constraints(nullable: "false")
            }
        }
    }

    // copy manual schedules from `Staff` to correspnding `Schedule`
    changeSet(author: "ericbai", id: "1551375338796-custom-9-1") {
        grailsChange {
            change {
                sql.rows("SELECT * FROM staff").each { GroovyRowResult thisStaff ->
                    sql.executeUpdate([
                        scheduleId: thisStaff.schedule_id,
                        useManual: thisStaff.manual_schedule,
                        isAvailable: thisStaff.is_available
                    ], """
                        UPDATE schedule
                        SET manual = :useManual
                            AND manual_is_available = :isAvailable
                        WHERE id = :scheduleId
                    """)
                }
            }
            // no need to copy back boolean flags because we never erased them on `Staff`s
        }
    }

    // copy manual schedules from `OwnerPolicy` to correspnding `Schedule`
    changeSet(author: "ericbai", id: "1551375338796-custom-9-2") {
        grailsChange {
            change {
                sql.rows("""
                    SELECT *
                    FROM owner_policy
                    WHERE schedule_id IS NOT NULL
                """).each { GroovyRowResult thisPolicy ->
                    sql.executeUpdate([
                        scheduleId: thisPolicy.schedule_id,
                        useManual: thisPolicy.manual_schedule,
                        isAvailable: thisPolicy.is_available
                    ], """
                        UPDATE schedule
                        SET manual = :useManual
                            AND manual_is_available = :isAvailable
                        WHERE id = :scheduleId
                    """)
                }
            }
            // no need to copy back boolean flags because we never erased them on `OwnerPolicy`s
        }
    }

    // find all phones that each staff member has access to and fill in schedule info if not present
    changeSet(author: "ericbai", id: "1551375338796-custom-10") {
        grailsChange {
            change {
                // step 1: find all staffs
                sql.rows("SELECT * FROM staff").each { GroovyRowResult thisStaff ->
                    // step 2: find all phones this staff member has access to
                    List<Long> phoneOwnerIds = sql.rows([staffId: thisStaff.id], """
                        SELECT po.id AS phone_owner_id
                        FROM phone_ownership AS po
                        LEFT JOIN staff AS s ON s.id = po.owner_id
                        LEFT JOIN team AS t ON t.id = po.owner_id
                        WHERE (po.type = 'INDIVIDUAL' AND s.id = :staffId)
                            OR (po.type = 'GROUP' AND t.id IN (SELECT team_members_id
                                FROM team_staff
                                WHERE staff_id = :staffId))
                    """)*.phone_owner_id ?: []
                    // step 3: find existing policies
                    List<GroovyRowResult> existingPolicies = sql.rows([staffId: thisStaff.id], """
                        SELECT *
                        FROM owner_policy
                        WHERE staff_id = :staffId
                    """)
                    existingPolicies.each { GroovyRowResult thisPolicy ->
                        // only copy schedule over from staff if this policy doesn't already have a schedule
                        if (!thisPolicy.schedule_id) {
                            Long newScheduleId = doCopySchedule(sql, thisStaff.schedule_id)
                            sql.executeUpdate([policyId: thisPolicy.id, scheduleId: newScheduleId], """
                                UPDATE owner_policy
                                SET schedule_id = :scheduleId
                                WHERE id = :policyId
                            """)
                        }
                    }
                    // step 4: find phones missing a policy
                    (phoneOwnerIds - existingPolicies*.owner_id).each { Long missingPolicyOwnerId ->
                        Long newScheduleId = doCopySchedule(sql, thisStaff.schedule_id)
                        sql.executeInsert([
                            version: 0,
                            frequency: DefaultOwnerPolicy.DEFAULT_FREQUENCY.toString(),
                            level: DefaultOwnerPolicy.DEFAULT_LEVEL.toString(),
                            method: DefaultOwnerPolicy.DEFAULT_METHOD.toString(),
                            ownerId: missingPolicyOwnerId,
                            scheduleId: newScheduleId,
                            shouldSendPreviewLink: DefaultOwnerPolicy.DEFAULT_SEND_PREVIEW_LINK.toString() ? 1 : 0,
                            staffId: thisStaff.id
                        ], """
                            INSERT INTO owner_policy (
                                version,
                                frequency,
                                level,
                                method,
                                owner_id,
                                schedule_id,
                                should_send_preview_link,
                                staff_id)
                            VALUES (
                                :version,
                                :frequency,
                                :level,
                                :method,
                                :ownerId,
                                :scheduleId,
                                :shouldSendPreviewLink,
                                :staffId)
                        """)
                    }
                }
            }
            rollback {
                List<GroovyRowResult> staffsWithNoSchedule = sql.rows("""
                    SELECT *
                    FROM staff
                    WHERE schedule_id IS NULL
                """)
                staffsWithNoSchedule.each { GroovyRowResult thisStaff ->
                    Long newScheduleId = doCopySchedule(sql, null)
                    sql.executeUpdate([staffId: thisStaff.id, scheduleId: newScheduleId], """
                        UPDATE staff
                        SET schedule_id = :scheduleId
                        WHERE id = :staffId
                    """)
                }
            }
        }
    }

    // owner policies require an associated schedule
    changeSet(author: "ericbai", id: "1551375338796-custom-11") {
        grailsChange {
            change {
                List<GroovyRowResult> policiesWithNoSchedule = sql.rows("""
                    SELECT *
                    FROM owner_policy
                    WHERE schedule_id IS NULL
                """)
                policiesWithNoSchedule.each { GroovyRowResult thisPolicy ->
                    Long newScheduleId = doCopySchedule(sql, null)
                    sql.executeUpdate([policyId: thisPolicy.id, scheduleId: newScheduleId], """
                        UPDATE owner_policy
                        SET schedule_id = :scheduleId
                        WHERE id = :policyId
                    """)
                }
            }
            // it's fine to have some orphan schedules in the worst case on rollback
        }
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-38") {
        dropForeignKeyConstraint(baseTableName: "staff", baseTableSchemaName: "prodDb", constraintName: "FK_idvwygj7bloa7mqlwe6tmkpc8")
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-75") {
        dropColumn(columnName: "schedule_id", tableName: "staff")
    }

    // Make all `WeeklySchedule`s into `Schedule`s
    changeSet(author: "ericbai (generated)", id: "1551375338796-71") {
        dropColumn(columnName: "class", tableName: "schedule")
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-73-1") {
        dropColumn(columnName: "is_available", tableName: "staff")
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-73-2") {
        dropColumn(columnName: "manual_schedule", tableName: "staff")
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-73-3") {
        dropColumn(columnName: "use_staff_availability", tableName: "owner_policy")
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-73-4") {
        dropColumn(columnName: "is_available", tableName: "owner_policy")
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-73-5") {
        dropColumn(columnName: "manual_schedule", tableName: "owner_policy")
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-42") {
        addNotNullConstraint(columnDataType: "bigint", columnName: "schedule_id", tableName: "owner_policy")
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-17") {
        addNotNullConstraint(columnDataType: "varchar(255)", columnName: "friday", tableName: "schedule")
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-18") {
        addNotNullConstraint(columnDataType: "varchar(255)", columnName: "monday", tableName: "schedule")
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-19") {
        addNotNullConstraint(columnDataType: "varchar(255)", columnName: "saturday", tableName: "schedule")
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-20") {
        addNotNullConstraint(columnDataType: "varchar(255)", columnName: "sunday", tableName: "schedule")
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-21") {
        addNotNullConstraint(columnDataType: "varchar(255)", columnName: "thursday", tableName: "schedule")
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-22") {
        addNotNullConstraint(columnDataType: "varchar(255)", columnName: "tuesday", tableName: "schedule")
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-23") {
        addNotNullConstraint(columnDataType: "varchar(255)", columnName: "wednesday", tableName: "schedule")
    }
}

Long doCopySchedule(Sql sql, Long scheduleId) {
    GroovyRowResult existingSchedule = sql.rows([scheduleId: scheduleId], """
        SELECT *
        FROM schedule
        WHERE id = :scheduleId
    """)[0]
    sql.executeInsert([
        class: Schedule.class.canonicalName,
        version: 0,
        manual: existingSchedule?.manual != null ? existingSchedule.manual : 1,
        manualIsAvailable: existingSchedule?.manual_is_available != null ?
            existingSchedule.manual_is_available :
            1,
        sunday: existingSchedule?.sunday ?: "",
        monday: existingSchedule?.monday ?: "",
        tuesday: existingSchedule?.tuesday ?: "",
        wednesday: existingSchedule?.wednesday ?: "",
        thursday: existingSchedule?.thursday ?: "",
        friday: existingSchedule?.friday ?: "",
        saturday: existingSchedule?.saturday ?: ""
    ],"""
        INSERT INTO schedule (class,
            version,
            manual,
            manual_is_available,
            sunday,
            monday,
            tuesday,
            wednesday,
            thursday,
            friday,
            saturday)
        VALUES (:class,
            :version,
            :manual,
            :manualIsAvailable,
            :sunday,
            :monday,
            :tuesday,
            :wednesday,
            :thursday,
            :friday,
            :saturday)
    """)[0][0]
}
