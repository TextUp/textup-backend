databaseChangeLog = {

	changeSet(author: "eb27 (generated)", id: "1468436629946-1") {
		createTable(tableName: "future_message") {
			column(autoIncrement: "true", name: "id", type: "bigint") {
				constraints(nullable: "false", primaryKey: "true", primaryKeyName: "future_messagPK")
			}

			column(name: "version", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "end_date", type: "datetime")

			column(name: "is_done", type: "bit") {
				constraints(nullable: "false")
			}

			column(name: "future_message_key", type: "varchar(255)") {
				constraints(nullable: "false")
			}

			column(name: "message", type: "varchar(320)") {
				constraints(nullable: "false")
			}

			column(name: "notify_self", type: "bit") {
				constraints(nullable: "false")
			}

			column(name: "record_id", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "start_date", type: "datetime") {
				constraints(nullable: "false")
			}

			column(name: "type", type: "varchar(255)") {
				constraints(nullable: "false")
			}

			column(name: "when_created", type: "datetime") {
				constraints(nullable: "false")
			}

			column(name: "class", type: "varchar(255)") {
				constraints(nullable: "false")
			}

			column(name: "repeat_count", type: "integer")

			column(name: "repeat_interval_in_millis", type: "bigint")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1468436629946-3") {
		createIndex(indexName: "FK_nvsbjq0gvjsblsskw5aoym4ld", tableName: "future_message") {
			column(name: "record_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1468436629946-2") {
		addForeignKeyConstraint(baseColumnNames: "record_id", baseTableName: "future_message", constraintName: "FK_nvsbjq0gvjsblsskw5aoym4ld", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "record", referencesUniqueColumn: "false")
	}
}
