databaseChangeLog = {

	changeSet(author: "eb27 (generated)", id: "1486059518641-1") {
		createTable(tableName: "record_note_revision") {
			column(autoIncrement: "true", name: "id", type: "bigint") {
				constraints(nullable: "false", primaryKey: "true", primaryKeyName: "record_note_rPK")
			}

			column(name: "version", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "author_id", type: "bigint")

			column(name: "author_name", type: "varchar(255)")

			column(name: "author_type", type: "varchar(255)")

			column(name: "image_keys_as_string", type: "varchar(255)")

			column(name: "location_id", type: "bigint")

			column(name: "note_id", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "note_contents", type: "varchar(1000)")

			column(name: "when_changed", type: "datetime") {
				constraints(nullable: "false")
			}
		}
	}

	changeSet(author: "eb27 (generated)", id: "1486059518641-2") {
		addColumn(tableName: "record_item") {
			column(name: "image_keys_as_string", type: "varchar(255)")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1486059518641-3") {
		addColumn(tableName: "record_item") {
			column(name: "is_deleted", type: "bit")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1486059518641-4") {
		addColumn(tableName: "record_item") {
			column(name: "location_id", type: "bigint")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1486059518641-5") {
		addColumn(tableName: "record_item") {
			column(name: "note_contents", type: "varchar(1000)")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1486059518641-6") {
		addColumn(tableName: "record_item") {
			column(name: "when_changed", type: "datetime")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1486059518641-34") {
		createIndex(indexName: "FK_ky4xtt5fjejskfentxu9ol4t", tableName: "record_item") {
			column(name: "location_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1486059518641-35") {
		createIndex(indexName: "FK_8iuq0e96ss9wf6tncx1od0ir", tableName: "record_note_revision") {
			column(name: "note_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1486059518641-36") {
		createIndex(indexName: "FK_iwi3hg6v11j3xyaypcjprma6n", tableName: "record_note_revision") {
			column(name: "location_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1486059518641-12") {
		addForeignKeyConstraint(baseColumnNames: "location_id", baseTableName: "record_item", constraintName: "FK_ky4xtt5fjejskfentxu9ol4t", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "location", referencesUniqueColumn: "false")
	}

	changeSet(author: "eb27 (generated)", id: "1486059518641-13") {
		addForeignKeyConstraint(baseColumnNames: "location_id", baseTableName: "record_note_revision", constraintName: "FK_iwi3hg6v11j3xyaypcjprma6n", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "location", referencesUniqueColumn: "false")
	}

	changeSet(author: "eb27 (generated)", id: "1486059518641-14") {
		addForeignKeyConstraint(baseColumnNames: "note_id", baseTableName: "record_note_revision", constraintName: "FK_8iuq0e96ss9wf6tncx1od0ir", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "record_item", referencesUniqueColumn: "false")
	}
}
