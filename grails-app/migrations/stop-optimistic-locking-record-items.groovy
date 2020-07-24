databaseChangeLog = {

	changeSet(author: "ericbai (generated)", id: "1595624160355-1") {
		dropColumn(columnName: "version", tableName: "phone_record")
	}

	changeSet(author: "ericbai (generated)", id: "1595624160355-2") {
		dropColumn(columnName: "version", tableName: "record")
	}

	changeSet(author: "ericbai (generated)", id: "1595624160355-3") {
		dropColumn(columnName: "version", tableName: "record_item")
	}

	changeSet(author: "ericbai (generated)", id: "1595624160355-4") {
		dropColumn(columnName: "version", tableName: "record_item_receipt")
	}
}
