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

    // copy manual schedules from staff to correspnding schedule
    changeSet(author: "ericbai", id: "1551375338796-custom-9") {
        grailsChange {
            change {
                List<GroovyRowResult> staffScheduleInfo = sql.rows("""
                    SELECT schedule_id, manual_schedule, is_available
                    FROM staff
                """)
                staffScheduleInfo.each { GroovyRowResult row ->
                    sql.executeUpdate([
                        scheduleId: row.schedule_id,
                        useManual: row.manual_schedule,
                        isAvailable: row.is_available
                    ], """
                        UPDATE schedule
                        SET manual = :useManual
                            manual_is_available = :isAvailable
                        WHERE id = :scheduleId
                    """)
                }
            }
            // no need to copy back boolean flags because we never erased them on `Staff`s
        }
    }

    // find all phones that each staff member has access to and fill in schedule info if not present
    changeSet(author: "ericbai", id: "1551375338796-custom-10") {
        grailsChange {
            change {
                // step 1: find all staffs
                List<GroovyRowResult> staffs = sql.rows("""
                    SELECT id, schedule_id
                    FROM staff
                """)
                staffs.each { GroovyRowResult row1 ->
                    // step 2: find all teams this staff is on
                    List<Long> teamIds = sql.rows([staffId: row1.id], """
                        SELECT team_members_id
                        FROM team_staff
                        WHERE staff_id = :staffId
                    """)*.team_members_id ?: []
                    // step 3: find all phones this staff member has access to
                    List<Long> accessiblePhoneOwnerIds = sql.rows([
                        staffId: row1.id,
                        teamIds: teamIds
                    ], """
                        SELECT po.id
                        FROM phone_ownership AS po
                        LEFT JOIN staff AS s ON s.id = po.owner_id
                            AND po.type = 'INDIVIDUAL'
                            AND s.id = :staffId
                        LEFT JOIN team AS t ON t.id = po.owner_id
                            AND po.type = 'GROUP'
                            AND t.id IN :teamIds
                    """)*.id ?: []
                    // step 4: find existing policies with a null schedule
                    List<GroovyRowResult> policiesMissingSchedule = sql.rows([
                        ownerIds: accessiblePhoneOwnerIds
                    ], """
                        SELECT id, owner_id
                        FROM owner_policy
                        WHERE owner_id IN :ownerIds
                            AND schedule_id IS NULL
                    """)
                    policiesMissingSchedule.each { GroovyRowResult row2 ->
                        Long newScheduleId = doCopySchedule(row1.schedule_id)
                        sql.executeUpdate([policyId: row2.id, scheduleId: newScheduleId], """
                            UPDATE owner_policy
                            SET schedule_id = :scheduleId
                            WHERE id = :policyId
                        """)
                    }
                    // step 5: find phones missing a policy
                    (accessiblePhoneOwnerIds - policiesMissingSchedule*.owner_id)
                        .each { Long missingPolicyOwnerId ->
                            Long newScheduleId = doCopySchedule(row1.schedule_id)
                            sql.executeInsert([
                                version: 0,
                                frequency: DefaultOwnerPolicy.DEFAULT_FREQUENCY.toString(),
                                level: DefaultOwnerPolicy.DEFAULT_LEVEL.toString(),
                                method: DefaultOwnerPolicy.DEFAULT_METHOD.toString(),
                                ownerId: missingPolicyOwnerId,
                                scheduleId: newScheduleId,
                                shouldSendPreviewLink: DefaultOwnerPolicy.DEFAULT_SEND_PREVIEW_LINK.toString(),
                                staffId: row1.id
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
                List<GroovyRowResult> staffWithNoSchedule = sql.rows("""
                    SELECT id
                    FROM staff
                    WHERE schedule_id IS NULL
                """)
                staffWithNoSchedule.each { GroovyRowResult row ->
                    Long newScheduleId = doCopySchedule(null)
                    sql.executeUpdate([staffId: row.id, scheduleId: newScheduleId], """
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
                    SELECT id
                    FROM owner_policy
                    WHERE schedule_id IS NULL
                """)
                policiesWithNoSchedule.each { GroovyRowResult row ->
                    Long newScheduleId = doCopySchedule(null)
                    sql.executeUpdate([policyId: row.id, scheduleId: newScheduleId], """
                        UPDATE owner_policy
                        SET schedule_id = :scheduleId
                        WHERE id = :policyId
                    """)
                }
            }
            // it's fine to have some orphan schedules in the worst case on rollback
        }
    }

    Long doCopySchedule(Long scheduleId) {
        GroovyRowResult existingSchedule = sql.rows([scheduleId: scheduleId],
            "SELECT * FROM schedule where id = :scheduleId")[0]
        sql.executeInsert([
            version: 0,
            manual: existingSchedule?.manual != null ? existingSchedule.manual : true,
            manualIsAvailable: existingSchedule?.manualIsAvailable != null ? existingSchedule.manualIsAvailable : true,
            sunday: existingSchedule?.sunday ?: "",
            monday: existingSchedule?.monday ?: "",
            tuesday: existingSchedule?.tuesday ?: "",
            wednesday: existingSchedule?.wednesday ?: "",
            thursday: existingSchedule?.thursday ?: "",
            friday: existingSchedule?.friday ?: "",
            saturday: existingSchedule?.saturday ?: ""
        ],"""
        INSERT INTO schedule (
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
        VALUES (
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
    """)
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

    changeSet(author: "ericbai (generated)", id: "1551375338796-72") {
        dropColumn(columnName: "is_available", tableName: "staff")
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-73") {
        dropColumn(columnName: "manual_schedule", tableName: "staff")
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-72") {
        dropColumn(columnName: "use_staff_availability", tableName: "owner_policy")
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-73") {
        dropColumn(columnName: "is_available", tableName: "owner_policy")
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-73") {
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
