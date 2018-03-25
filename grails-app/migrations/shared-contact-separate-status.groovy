databaseChangeLog = {

	changeSet(author: "ericbai (generated)", id: "1522003962311-1") {
		addColumn(tableName: "shared_contact") {
			column(name: "status", type: "varchar(255)", defaultValue: "ACTIVE") {
				constraints(nullable: "false")
			}
		}
	}
}
