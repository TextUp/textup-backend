databaseChangeLog = {

	changeSet(author: "ericbai (generated)", id: "1545961585587-1") {
		addColumn(tableName: "phone") {
			column(name: "custom_account_id", type: "varchar(255)")
		}
	}

	changeSet(author: "ericbai (generated)", id: "1545961585587-2") {
		addNotNullConstraint(columnDataType: "varchar(320)", columnName: "away_message", tableName: "phone")
	}
}
