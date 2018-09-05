databaseChangeLog = {

	changeSet(author: "ericbai (generated)", id: "1536177235225-1") {
		addColumn(tableName: "media_element_version") {
			column(name: "height_in_pixels", type: "integer")
		}
	}

	changeSet(author: "ericbai (generated)", id: "1536177235225-2") {
		addNotNullConstraint(columnDataType: "varchar(255)", columnName: "contact_number_as_string", tableName: "record_item_receipt")
	}

    // `migrate-existing-media.groovy` set the type of all existing `MediaElement`s to be `IMAGE`
    // now that we've refactored that enum to be the specific MIME types, we need to update
    // all existing types to be `IMAGE_UNKNOWN`
    changeSet(author: "ericbai", id: "1536177235225-3") {
        grailsChange {
            change {
                sql.execute """
                    UPDATE media_element
                    SET type = "IMAGE_UNKNOWN"
                    WHERE type IS NULL OR type = "IMAGE";
                """
            }
            rollback {
                sql.execute """
                    UPDATE media_element
                    SET type = "IMAGE"
                    WHERE type = "IMAGE_UNKNOWN";
                """
            }
        }
    }
}
