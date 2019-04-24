databaseChangeLog = {

	changeSet(author: "ericbai", id: "1555777588884-1") {
		renameColumn(tableName: "future_message",
            oldColumnName: "notify_self",
            newColumnName: "notify_self_on_send",
            columnDataType: "bit")
	}

	changeSet(author: "ericbai", id: "1555777588884-2") {
		addNotNullConstraint(tableName: "future_message",
			columnName: "notify_self_on_send",
			columnDataType: "bit")
	}
}
