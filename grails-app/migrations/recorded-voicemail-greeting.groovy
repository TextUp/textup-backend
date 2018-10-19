databaseChangeLog = {

    changeSet(author: "ericbai (generated)", id: "1539956368803-3") {
        addColumn(tableName: "media_element_version") {
            column(name: "is_public", type: "bit", defaultValueBoolean: false) {
                constraints(nullable: "false")
            }
        }
    }

    changeSet(author: "ericbai (generated)", id: "1539956368803-6") {
        addColumn(tableName: "phone") {
            column(name: "use_voicemail_recording_if_present", type: "bit", defaultValueBoolean: false) {
                constraints(nullable: "false")
            }
        }
    }

    changeSet(author: "ericbai (generated)", id: "1539956368803-5") {
        addColumn(tableName: "phone") {
            column(name: "media_id", type: "bigint")
        }
    }

    changeSet(author: "ericbai (generated)", id: "1539956368803-12") {
        createIndex(indexName: "FK_3bo5vo79h9ofamfw7flxecbxs", tableName: "phone") {
            column(name: "media_id")
        }
    }

    changeSet(author: "ericbai (generated)", id: "1539956368803-10") {
        addForeignKeyConstraint(baseColumnNames: "media_id", baseTableName: "phone", constraintName: "FK_3bo5vo79h9ofamfw7flxecbxs", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "media_info", referencesUniqueColumn: "false")
    }
}
