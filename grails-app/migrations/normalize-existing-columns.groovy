databaseChangeLog = {
    changeSet(author: "ericbai", id: "1533676560477-8") {
        renameColumn(tableName: "record_item_receipt",
            oldColumnName: "received_by_as_string",
            newColumnName: "contact_number_as_string",
            columnDataType: "varchar(255)")
    }

    changeSet(author: "ericbai (generated)", id: "1533676560477-10") {
        dropNotNullConstraint(columnDataType: "varchar(320)", columnName: "message", tableName: "future_message")
    }

    changeSet(author: "ericbai (generated)", id: "1533676560477-11") {
        modifyDataType(columnName: "note_contents", newDataType: "longtext", tableName: "record_item")
    }

    changeSet(author: "ericbai (generated)", id: "1533676560477-12") {
        modifyDataType(columnName: "note_contents", newDataType: "longtext", tableName: "record_note_revision")
    }

    changeSet(author: "ericbai", id: "1533676560477-29") {
        grailsChange {
            change {
                sql.execute """
                    UPDATE record_item
                    SET note_contents = call_contents
                    WHERE call_contents IS NOT NULL;
                """
                sql.execute """
                    ALTER TABLE record_item
                    DROP COLUMN call_contents;
                """
            }
            rollback {
                sql.execute """
                    ALTER TABLE record_item
                    ADD COLUMN call_contents VARCHAR(320) NULL;
                """
            }
        }
    }
}
