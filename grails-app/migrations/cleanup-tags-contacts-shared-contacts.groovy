databaseChangeLog = {

    changeSet(author: "ericbai (generated)", id: "1551375338796-27") {
        dropForeignKeyConstraint(baseTableName: "contact_tag", baseTableSchemaName: "prodDb", constraintName: "FK_omd2pr1ijl9467fgyrg0o414x")
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-28") {
        dropForeignKeyConstraint(baseTableName: "contact_tag", baseTableSchemaName: "prodDb", constraintName: "FK_74jr9a2pikdq0v6lv26x480l7")
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-29") {
        dropForeignKeyConstraint(baseTableName: "contact_tag_contact", baseTableSchemaName: "prodDb", constraintName: "FK_l0ckitkb43i7hlxaa8ada6xne")
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-30") {
        dropForeignKeyConstraint(baseTableName: "contact_tag_contact", baseTableSchemaName: "prodDb", constraintName: "FK_hw2mnvgpqrvkwvl92pvfcfhil")
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-24") {
        dropForeignKeyConstraint(baseTableName: "contact", baseTableSchemaName: "prodDb", constraintName: "FK_m2nh62y3a1b84ii70ry5teui8")
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-25") {
        dropForeignKeyConstraint(baseTableName: "contact", baseTableSchemaName: "prodDb", constraintName: "FK_7kcrj4x2fu8y1ixf5ouwiaksw")
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

    changeSet(author: "ericbai (generated)", id: "1551375338796-77") {
        dropTable(tableName: "contact_tag")
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-78") {
        dropTable(tableName: "contact_tag_contact")
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-76") {
        dropTable(tableName: "contact")
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-81") {
        dropTable(tableName: "shared_contact")
    }
}
