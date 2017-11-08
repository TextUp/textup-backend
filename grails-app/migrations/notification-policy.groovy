databaseChangeLog = {

	changeSet(author: "ericbai (generated)", id: "1506196842002-1") {
		createTable(tableName: "notification_policy") {
			column(autoIncrement: "true", name: "id", type: "bigint") {
				constraints(nullable: "false", primaryKey: "true", primaryKeyName: "notification_PK")
			}

			column(name: "version", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "blacklist_data", type: "longtext")

			column(name: "level", type: "varchar(255)") {
				constraints(nullable: "false")
			}

			column(name: "staff_id", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "whitelist_data", type: "longtext")
		}
	}

	changeSet(author: "ericbai (generated)", id: "1506196842002-2") {
		createTable(tableName: "phone_ownership_notification_policy") {
			column(name: "phone_ownership_policies_id", type: "bigint")

			column(name: "notification_policy_id", type: "bigint")
		}
	}

	changeSet(author: "ericbai (generated)", id: "1506196842002-3") {
		addColumn(tableName: "contact") {
			column(name: "is_deleted", type: "bit") {
				constraints(nullable: "false")
			}
		}
	}

	changeSet(author: "ericbai (generated)", id: "1506196842002-4") {
		addColumn(tableName: "record_item") {
			column(name: "call_contents", type: "varchar(320)")
		}
	}

	changeSet(author: "ericbai (generated)", id: "1506196842002-5") {
		addColumn(tableName: "record_item") {
			column(name: "is_read_only", type: "bit", valueBoolean: false)
		}
	}

	changeSet(author: "ericbai (generated)", id: "1506196842002-32") {
		createIndex(indexName: "FK_crjwg1hjrxd6pr1iw93g8sk9l", tableName: "phone_ownership_notification_policy") {
			column(name: "notification_policy_id")
		}
	}

	changeSet(author: "ericbai (generated)", id: "1506196842002-33") {
		createIndex(indexName: "FK_l3nesjxr17d7qijoxu9n6hfxg", tableName: "phone_ownership_notification_policy") {
			column(name: "phone_ownership_policies_id")
		}
	}

	changeSet(author: "ericbai (generated)", id: "1506196842002-11") {
		addForeignKeyConstraint(baseColumnNames: "notification_policy_id", baseTableName: "phone_ownership_notification_policy", constraintName: "FK_crjwg1hjrxd6pr1iw93g8sk9l", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "notification_policy", referencesUniqueColumn: "false")
	}

	changeSet(author: "ericbai (generated)", id: "1506196842002-12") {
		addForeignKeyConstraint(baseColumnNames: "phone_ownership_policies_id", baseTableName: "phone_ownership_notification_policy", constraintName: "FK_l3nesjxr17d7qijoxu9n6hfxg", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "phone_ownership", referencesUniqueColumn: "false")
	}
}
