databaseChangeLog = {

	changeSet(author: "eb27 (generated)", id: "1478181350920-1") {
		modifyDataType(columnName: "string_data", newDataType: "longtext", tableName: "token")
	}

	changeSet(author: "eb27 (generated)", id: "1478181350920-2") {
		addNotNullConstraint(columnDataType: "longtext", columnName: "string_data", tableName: "token")
	}
}
