databaseChangeLog = {
	changeSet(author: "ericbai", id: "1536195767201-1") {
		renameColumn(tableName: "media_element_version",
            oldColumnName: "key",
            newColumnName: "version_id",
            columnDataType: "varchar(255)")
	}
    changeSet(author: "ericbai", id: "1536195767201-2") {
        addNotNullConstraint(columnDataType: "varchar(255)", columnName: "version_id", tableName: "media_element_version")
    }
}
