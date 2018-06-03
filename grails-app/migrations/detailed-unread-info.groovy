databaseChangeLog = {

	// CANNOT make this value dynamically calculated because the checksums would not match up
	// and the app would throw an error on start for all future deployments
	String defaultTouched = "2018-06-03 00:00:00"

	changeSet(author: "ericbai (generated)", id: "1528047225555-1") {
		addColumn(tableName: "contact") {
			column(name: "last_touched", type: "datetime", defaultValue:defaultTouched) {
				constraints(nullable: "false")
			}
		}
	}

	changeSet(author: "ericbai (generated)", id: "1528047225555-2") {
		addColumn(tableName: "shared_contact") {
			column(name: "last_touched", type: "datetime", defaultValue:defaultTouched) {
				constraints(nullable: "false")
			}
		}
	}
}
