databaseChangeLog = {

	changeSet(author: "eb27 (generated)", id: "1476806271658-1") {
		modifyDataType(columnName: "contents", newDataType: "longtext", tableName: "record_item")
	}
}
