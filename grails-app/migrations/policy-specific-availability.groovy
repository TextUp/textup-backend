databaseChangeLog = {

	changeSet(author: "ericbai (generated)", id: "1526847697618-1") {
		addColumn(tableName: "notification_policy") {
			column(name: "is_available", type: "bit", defaultValueBoolean: true) {
				constraints(nullable: "false")
			}
		}
	}

	changeSet(author: "ericbai (generated)", id: "1526847697618-2") {
		addColumn(tableName: "notification_policy") {
			column(name: "manual_schedule", type: "bit", defaultValueBoolean: true) {
				constraints(nullable: "false")
			}
		}
	}

	changeSet(author: "ericbai (generated)", id: "1526847697618-3") {
		addColumn(tableName: "notification_policy") {
			column(name: "schedule_id", type: "bigint")
		}
	}

	changeSet(author: "ericbai (generated)", id: "1526847697618-4") {
		addColumn(tableName: "notification_policy") {
			column(name: "use_staff_availability", type: "bit", defaultValueBoolean: true) {
				constraints(nullable: "false")
			}
		}
	}

	changeSet(author: "ericbai (generated)", id: "1526847697618-6") {
		createIndex(indexName: "FK_d7c1u3s1m3j9y64wjvo9565tw", tableName: "notification_policy") {
			column(name: "schedule_id")
		}
	}

	changeSet(author: "ericbai (generated)", id: "1526847697618-5") {
		addForeignKeyConstraint(baseColumnNames: "schedule_id", baseTableName: "notification_policy", constraintName: "FK_d7c1u3s1m3j9y64wjvo9565tw", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "schedule", referencesUniqueColumn: "false")
	}
}
