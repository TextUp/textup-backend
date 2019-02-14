databaseChangeLog = {

	changeSet(author: "ericbai (generated)", id: "1550108039080-1") {
		modifyDataType(columnName: "is_deleted", newDataType: "bit", tableName: "record_item")
	}

	changeSet(author: "ericbai (generated)", id: "1550108039080-2") {
		addNotNullConstraint(columnDataType: "bit", columnName: "is_deleted", tableName: "record_item")
	}
}
