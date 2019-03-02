databaseChangeLog = {

    changeSet(author: "ericbai (generated)", id: "1551375338796-24") {
        dropForeignKeyConstraint(baseTableName: "contact", baseTableSchemaName: "prodDb", constraintName: "FK_m2nh62y3a1b84ii70ry5teui8")
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-25") {
        dropForeignKeyConstraint(baseTableName: "contact", baseTableSchemaName: "prodDb", constraintName: "FK_7kcrj4x2fu8y1ixf5ouwiaksw")
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-26") {
        dropForeignKeyConstraint(baseTableName: "contact_number", baseTableSchemaName: "prodDb", constraintName: "FK_1cjhdw1dw396gqxyeqbv7x863")
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-76") {
        dropTable(tableName: "contact")
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-39") {
        addForeignKeyConstraint(baseColumnNames: "owner_id", baseTableName: "contact_number", constraintName: "FK_1cjhdw1dw396gqxyeqbv7x863", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "phone_record", referencesUniqueColumn: "false")
    }
}
