databaseChangeLog = {

	changeSet(author: "ericbai (generated)", id: "1540321995267-1") {
		addColumn(tableName: "record_item") {
			column(name: "was_scheduled", type: "bit", defaultValueBoolean: false) {
				constraints(nullable: "false")
			}
		}
	}
}
