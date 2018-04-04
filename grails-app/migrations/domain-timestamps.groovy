databaseChangeLog = {

	// CANNOT make this value dynamically calculated because the checksums would not match up
	// and the app would throw an error on start for all future deployments
	String defaultNow = "2018-04-02 03:22:17"

	changeSet(author: "ericbai (generated)", id: "1522633873471-1") {
		addColumn(tableName: "contact") {
			column(name: "when_created", type: "datetime", defaultValue:defaultNow) {
				constraints(nullable: "false")
			}
		}
	}

	changeSet(author: "ericbai (generated)", id: "1522633873471-2") {
		addColumn(tableName: "contact_tag") {
			column(name: "when_created", type: "datetime", defaultValue:defaultNow) {
				constraints(nullable: "false")
			}
		}
	}

	changeSet(author: "ericbai (generated)", id: "1522633873471-3") {
		addColumn(tableName: "organization") {
			column(name: "when_created", type: "datetime", defaultValue:defaultNow) {
				constraints(nullable: "false")
			}
		}
	}

	changeSet(author: "ericbai (generated)", id: "1522633873471-4") {
		addColumn(tableName: "phone") {
			column(name: "when_created", type: "datetime", defaultValue:defaultNow) {
				constraints(nullable: "false")
			}
		}
	}

	changeSet(author: "ericbai (generated)", id: "1522633873471-5") {
		addColumn(tableName: "staff") {
			column(name: "when_created", type: "datetime", defaultValue:defaultNow) {
				constraints(nullable: "false")
			}
		}
	}

	changeSet(author: "ericbai (generated)", id: "1522633873471-6") {
		addColumn(tableName: "team") {
			column(name: "when_created", type: "datetime", defaultValue:defaultNow) {
				constraints(nullable: "false")
			}
		}
	}
}
