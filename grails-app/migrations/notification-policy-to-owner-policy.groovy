import groovy.sql.*
import org.textup.*

databaseChangeLog = {

    changeSet(author: "ericbai", id: "1551375338796-custom-3") {
        renameTable(oldTableName: "notification_policy", newTableName: "owner_policy")
    }

    changeSet(author: "ericbai", id: "1551375338796-custom-4") {
        addColumn(tableName: "owner_policy") {
            column(name: "frequency",
                type: "varchar(255)",
                value: DefaultOwnerPolicy.DEFAULT_FREQUENCY.toString()) {
                constraints(nullable: "false")
            }
        }
    }

    changeSet(author: "ericbai", id: "1551375338796-custom-5") {
        addColumn(tableName: "owner_policy") {
            column(name: "method",
                type: "varchar(255)",
                value: DefaultOwnerPolicy.DEFAULT_METHOD.toString()) {
                constraints(nullable: "false")
            }
        }
    }

    changeSet(author: "ericbai", id: "1551375338796-custom-6") {
        addColumn(tableName: "owner_policy") {
            // because of `belongsTo` relationship implies a `hasOne` relationship, no join table
            column(name: "owner_id", type: "bigint") {
                constraints(nullable: "false")
            }
        }
    }

    changeSet(author: "ericbai", id: "1551375338796-custom-7") {
        addColumn(tableName: "owner_policy") {
            column(name: "should_send_preview_link",
                type: "bit",
                valueBoolean: true) {
                constraints(nullable: "false")
            }
        }
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-2") {
        createTable(tableName: "owner_policy_blacklist") {
            column(name: "owner_policy_id", type: "bigint")

            column(name: "blacklist_long", type: "bigint")
        }
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-3") {
        createTable(tableName: "owner_policy_whitelist") {
            column(name: "owner_policy_id", type: "bigint")

            column(name: "whitelist_long", type: "bigint")
        }
    }

    changeSet(author: "ericbai", id: "1551375338796-custom-8") {
        grailsChange {
            change {
                // migrate phone-ownership association from join table to foreign key
                sql.rows("SELECT * FROM phone_ownership_notification_policy")
                    .each { GroovyRowResult joinEntry ->
                        sql.executeUpdate([
                            ownerId: joinEntry.phone_ownership_policies_id,
                            policyId: joinEntry.notification_policy_id
                        ], """
                            UPDATE owner_policy
                            SET owner_id = :ownerId
                            WHERE id = :policyId
                        """)
                    }
            }
            rollback {
                sql.rows("SELECT * FROM owner_policy").each { GroovyRowResult ownerPolicy ->
                    sql.executeInsert([
                        ownerId: ownerPolicy.owner_id,
                        policyId: ownerPolicy.id
                    ], """
                        INSERT INTO phone_ownership_notification_policy(phone_ownership_policies_id,
                            notification_policy_id)
                        VALUES (:ownerId,
                            :policyId)
                    """)
                }
            }
        }
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-55") {
        createIndex(indexName: "FK_iao86rs6i8uj8p9cm8ydaxhnj", tableName: "owner_policy") {
            column(name: "staff_id")
        }
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-56") {
        createIndex(indexName: "FK_myg15dt0vd5xmjghbm1p36r00", tableName: "owner_policy") {
            column(name: "owner_id")
        }
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-58") {
        createIndex(indexName: "FK_4p1nh6xptmt9bjql3wjqqy4jd", tableName: "owner_policy_blacklist") {
            column(name: "owner_policy_id")
        }
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-59") {
        createIndex(indexName: "FK_jma02a9q01wbcnegmb6qlfyoo", tableName: "owner_policy_whitelist") {
            column(name: "owner_policy_id")
        }
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-33") {
        dropForeignKeyConstraint(baseTableName: "phone_ownership_notification_policy", baseTableSchemaName: "prodDb", constraintName: "FK_crjwg1hjrxd6pr1iw93g8sk9l")
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-34") {
        dropForeignKeyConstraint(baseTableName: "phone_ownership_notification_policy", baseTableSchemaName: "prodDb", constraintName: "FK_l3nesjxr17d7qijoxu9n6hfxg")
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-75-1") {
        dropColumn(columnName: "blacklist_data", tableName: "owner_policy")
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-75-2") {
        dropColumn(columnName: "whitelist_data", tableName: "owner_policy")
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-80") {
        dropTable(tableName: "phone_ownership_notification_policy")
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-41") {
        addForeignKeyConstraint(baseColumnNames: "owner_id", baseTableName: "owner_policy", constraintName: "FK_myg15dt0vd5xmjghbm1p36r00", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "phone_ownership", referencesUniqueColumn: "false")
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-43") {
        addForeignKeyConstraint(baseColumnNames: "staff_id", baseTableName: "owner_policy", constraintName: "FK_iao86rs6i8uj8p9cm8ydaxhnj", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "staff", referencesUniqueColumn: "false")
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-44") {
        addForeignKeyConstraint(baseColumnNames: "owner_policy_id", baseTableName: "owner_policy_blacklist", constraintName: "FK_4p1nh6xptmt9bjql3wjqqy4jd", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "owner_policy", referencesUniqueColumn: "false")
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-45") {
        addForeignKeyConstraint(baseColumnNames: "owner_policy_id", baseTableName: "owner_policy_whitelist", constraintName: "FK_jma02a9q01wbcnegmb6qlfyoo", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "owner_policy", referencesUniqueColumn: "false")
    }
}
