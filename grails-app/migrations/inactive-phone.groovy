databaseChangeLog = {

	changeSet(author: "eb27 (generated)", id: "1466798752452-1") {
		dropNotNullConstraint(columnDataType: "varchar(255)", columnName: "number_as_string", tableName: "phone")
	}
}
