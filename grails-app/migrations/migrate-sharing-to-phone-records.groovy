databaseChangeLog = {

    // make team <-> team sharing possible
    changeSet(author: "ericbai (generated)", id: "1551375338796-11") {
        addColumn(tableName: "phone_ownership") {
            column(name: "allow_sharing_with_other_teams", type: "bit") {
                constraints(nullable: "false")
            }
        }
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-35") {
        dropForeignKeyConstraint(baseTableName: "shared_contact", baseTableSchemaName: "prodDb", constraintName: "FK_bnai663rn9nx40gq13m2uyb2n")
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-36") {
        dropForeignKeyConstraint(baseTableName: "shared_contact", baseTableSchemaName: "prodDb", constraintName: "FK_dr1wogxfb3nutwkcsyo7g92m")
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-37") {
        dropForeignKeyConstraint(baseTableName: "shared_contact", baseTableSchemaName: "prodDb", constraintName: "FK_bqfcufat8ao5c936xdg6wvfo0")
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-81") {
        dropTable(tableName: "shared_contact")
    }
}
