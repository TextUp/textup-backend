databaseChangeLog = {

	changeSet(author: "ericbai (generated)", id: "1537324573991-1") {
		addColumn(tableName: "record_item") {
			column(name: "num_notified", type: "integer", defaultValue: 0) {
				constraints(nullable: "false")
			}
		}
	}

	changeSet(author: "ericbai (generated)", id: "1537324573991-2") {
		addColumn(tableName: "record_item_receipt") {
			column(name: "num_segments", type: "integer")
		}
	}
}
