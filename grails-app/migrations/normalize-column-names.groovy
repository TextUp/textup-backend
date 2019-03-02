databaseChangeLog = {

    // because we no longer depend on database to order contact numbers
    changeSet(author: "ericbai (generated)", id: "1551375338796-68") {
        dropColumn(columnName: "numbers_idx", tableName: "contact_number")
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-16") {
        addNotNullConstraint(columnDataType: "bit",
            columnName: "is_deleted",
            tableName: "record_item")
    }

    changeSet(author: "ericbai", id: "1551375338796-custom-1") {
        grailsChange {
            change {
                sql.executeUpdate("""
                    UPDATE record_item
                    SET is_deleted = 0
                    WHERE is_deleted IS NULL
                """)
            }
            // rollback not needed because `is_deleted` will just be ignored for rows that don't need it
        }
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-9") {
        renameColumn(tableName: "featured_announcement",
            oldColumnName: "owner_id",
            newColumnName: "phone_id",
            columnDataType: "bigint")
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-10") {
        renameColumn(tableName: "location",
            oldColumnName: "lon",
            newColumnName: "lng",
            columnDataType: "decimal(19,2)")
    }

    changeSet(author: "ericbai (generated)", id: "1551375338796-14") {
        renameColumn(tableName: "staff",
            oldColumnName: "personal_phone_as_string",
            newColumnName: "personal_number_as_string",
            columnDataType: "varchar(255)")
    }
}
