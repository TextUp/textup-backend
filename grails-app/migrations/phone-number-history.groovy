import groovy.sql.*

databaseChangeLog = {

    changeSet(author: "ericbai (generated)", id: "1551375338796-4") {
        createTable(tableName: "phone_number_history") {
            column(autoIncrement: "true", name: "id", type: "bigint") {
                constraints(nullable: "false", primaryKey: "true", primaryKeyName: "phone_number_PK")
            }

            column(name: "version", type: "bigint") {
                constraints(nullable: "false")
            }

            column(name: "end_time", type: "datetime")

            column(name: "number_as_string", type: "varchar(255)")

            column(name: "when_created", type: "datetime") {
                constraints(nullable: "false")
            }
        }
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-5") {
        createTable(tableName: "phone_phone_number_history") {
            column(name: "phone_number_history_entries_id", type: "bigint")

            column(name: "phone_number_history_id", type: "bigint")
        }
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-60") {
        createIndex(indexName: "FK_9va94w0kcg7jo5ev4hgisw31o", tableName: "phone_phone_number_history") {
            column(name: "phone_number_history_id")
        }
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-61") {
        createIndex(indexName: "FK_k009v16kaln7tmv03sn0vlmry", tableName: "phone_phone_number_history") {
            column(name: "phone_number_history_entries_id")
        }
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-46") {
        addForeignKeyConstraint(baseColumnNames: "phone_number_history_entries_id", baseTableName: "phone_phone_number_history", constraintName: "FK_k009v16kaln7tmv03sn0vlmry", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "phone", referencesUniqueColumn: "false")
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-47") {
        addForeignKeyConstraint(baseColumnNames: "phone_number_history_id", baseTableName: "phone_phone_number_history", constraintName: "FK_9va94w0kcg7jo5ev4hgisw31o", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "phone_number_history", referencesUniqueColumn: "false")
    }

    changeSet(author: "ericbai", id: "1551375338796-custom-2") {
        grailsChange {
            change {
                // step 1: fetch all phones no matter status
                sql.rows("SELECT * FROM phone").each { GroovyRowResult thisPhone ->
                    // step 2: create a history entry for each phone
                    Long phoneHistoryId = sql.executeInsert([
                            version: 0,
                            whenCreated: "2010-01-01 00:00:00", // this must be at the beginning of the month
                            phoneNumber: thisPhone.number_as_string
                        ], """
                        INSERT INTO phone_number_history (version,
                            when_created,
                            number_as_string)
                        VALUES (:version,
                            :whenCreated,
                            :phoneNumber)
                    """)[0][0]
                    // step 3: add to the join table to associate history entry with phone
                    sql.executeInsert([
                        phoneId: thisPhone.id,
                        historyId: phoneHistoryId
                    ], """
                        INSERT INTO phone_phone_number_history (phone_number_history_entries_id,
                            phone_number_history_id)
                        VALUES (:phoneId,
                            :historyId)
                    """)
                }
            }
            // rollback is when the `phone_number_history` and `phone_phone_number_history` tables are dropped
        }
    }
}
