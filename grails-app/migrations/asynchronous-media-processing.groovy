databaseChangeLog = {
    // CANNOT make this value dynamically calculated because the checksums would not match up
    // and the app would throw an error on start for all future deployments
    String defaultNow = "2018-10-20 03:22:17"
    changeSet(author: "ericbai (generated)", id: "1539956368803-1") {
        addColumn(tableName: "media_element") {
            column(name: "when_created", type: "datetime", defaultValue: defaultNow) {
                constraints(nullable: "false")
            }
        }
    }

    changeSet(author: "ericbai (generated)", id: "1539956368803-7") {
        dropNotNullConstraint(columnDataType: "bigint", columnName: "send_version_id", tableName: "media_element")
    }

    changeSet(author: "ericbai (generated)", id: "1539956368803-15") {
        dropColumn(columnName: "media_version", tableName: "media_element_version")
    }

    changeSet(author: "ericbai", id: "1539956368803-14") {
        renameColumn(tableName: "media_element_media_element_version",
            oldColumnName: "media_element_display_versions_id",
            newColumnName: "media_element_alternate_versions_id",
            columnDataType: "bigint")
    }
}
