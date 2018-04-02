databaseChangeLog = {

	changeSet(author: "ericbai (generated)", id: "1522599767524-1") {
		addColumn(tableName: "future_message") {
			column(name: "daylight_savings_zone", type: "varchar(255)")
		}
	}

	changeSet(author: "ericbai (generated)", id: "1522599767524-2") {
		addColumn(tableName: "future_message") {
			column(name: "has_adjusted_daylight_savings", type: "bit") {
				constraints(nullable: "false")
			}
		}
	}

	changeSet(author: "ericbai (generated)", id: "1522599767524-3") {
		addColumn(tableName: "future_message") {
			column(name: "when_adjust_daylight_savings", type: "datetime")
		}
	}
}
