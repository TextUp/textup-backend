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

    changeSet(author: "ericbai (generated)", id: "1551375338796-77") {
        dropTable(tableName: "contact_tag")
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-78") {
        dropTable(tableName: "contact_tag_contact")
    }
}
