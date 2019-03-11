databaseChangeLog = {

	// make team <-> team sharing possible
    changeSet(author: "ericbai (generated)", id: "1551375338796-11") {
        addColumn(tableName: "phone_ownership") {
            column(name: "allow_sharing_with_other_teams", type: "bit") {
                constraints(nullable: "false")
            }
        }
    }

	changeSet(author: "ericbai (generated)", id: "1551375338796-6") {
		createTable(tableName: "phone_record") {
			column(autoIncrement: "true", name: "id", type: "bigint") {
				constraints(nullable: "false", primaryKey: "true", primaryKeyName: "phone_recordPK")
			}

			column(name: "version", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "date_expired", type: "datetime")

			column(name: "last_touched", type: "datetime") {
				constraints(nullable: "false")
			}

			column(name: "permission", type: "varchar(255)")

			column(name: "phone_id", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "record_id", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "share_source_id", type: "bigint")

			column(name: "status", type: "varchar(255)") {
				constraints(nullable: "false")
			}

			column(name: "when_created", type: "datetime") {
				constraints(nullable: "false")
			}

			column(name: "class", type: "varchar(255)") {
				constraints(nullable: "false")
			}

			column(name: "group_hex_color", type: "varchar(255)")

			column(name: "is_deleted", type: "bit")

			column(name: "members_id", type: "bigint")

			column(name: "name", type: "varchar(255)")

			column(name: "individual_note", type: "longtext")
		}
	}

	changeSet(author: "ericbai (generated)", id: "1551375338796-7") {
		createTable(tableName: "phone_record_members") {
			column(autoIncrement: "true", name: "id", type: "bigint") {
				constraints(nullable: "false", primaryKey: "true", primaryKeyName: "phone_record_PK")
			}

			column(name: "version", type: "bigint") {
				constraints(nullable: "false")
			}
		}
	}

	changeSet(author: "ericbai (generated)", id: "1551375338796-8") {
		createTable(tableName: "phone_record_members_phone_record") {
			column(name: "phone_record_members_phone_records_id", type: "bigint")

			column(name: "phone_record_id", type: "bigint")
		}
	}

	changeSet(author: "ericbai (generated)", id: "1551375338796-62") {
		createIndex(indexName: "FK_6ie3n98wyt9m2nkhkbnvk8w1j", tableName: "phone_record") {
			column(name: "share_source_id")
		}
	}

	changeSet(author: "ericbai (generated)", id: "1551375338796-63") {
		createIndex(indexName: "FK_kyr0l0hxdthh6puwtar1d7629", tableName: "phone_record") {
			column(name: "members_id")
		}
	}

	changeSet(author: "ericbai (generated)", id: "1551375338796-64") {
		createIndex(indexName: "FK_lsxgs7the8ax6ts4xrn9c3lgx", tableName: "phone_record") {
			column(name: "record_id")
		}
	}

	changeSet(author: "ericbai (generated)", id: "1551375338796-65") {
		createIndex(indexName: "FK_retm7mnkxm8j77tnijmj24jgb", tableName: "phone_record") {
			column(name: "phone_id")
		}
	}

	changeSet(author: "ericbai (generated)", id: "1551375338796-66") {
		createIndex(indexName: "FK_bohsc8q4ufjq5t8435gqbwkep", tableName: "phone_record_members_phone_record") {
			column(name: "phone_record_id")
		}
	}

	changeSet(author: "ericbai (generated)", id: "1551375338796-67") {
		createIndex(indexName: "FK_h6f58d9i5m61blvtqyh861j9k", tableName: "phone_record_members_phone_record") {
			column(name: "phone_record_members_phone_records_id")
		}
	}

	// need to drop foreign key constraint on `contact_number` before we can add another one
	changeSet(author: "ericbai (generated)", id: "1551375338796-26") {
        dropForeignKeyConstraint(baseTableName: "contact_number", baseTableSchemaName: "prodDb", constraintName: "FK_1cjhdw1dw396gqxyeqbv7x863")
    }

	changeSet(author: "ericbai (generated)", id: "1551375338796-39") {
        addForeignKeyConstraint(baseColumnNames: "owner_id", baseTableName: "contact_number", constraintName: "FK_1cjhdw1dw396gqxyeqbv7x863", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "phone_record", referencesUniqueColumn: "false")
    }

	changeSet(author: "ericbai (generated)", id: "1551375338796-48") {
		addForeignKeyConstraint(baseColumnNames: "members_id", baseTableName: "phone_record", constraintName: "FK_kyr0l0hxdthh6puwtar1d7629", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "phone_record_members", referencesUniqueColumn: "false")
	}

	changeSet(author: "ericbai (generated)", id: "1551375338796-49") {
		addForeignKeyConstraint(baseColumnNames: "phone_id", baseTableName: "phone_record", constraintName: "FK_retm7mnkxm8j77tnijmj24jgb", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "phone", referencesUniqueColumn: "false")
	}

	changeSet(author: "ericbai (generated)", id: "1551375338796-50") {
		addForeignKeyConstraint(baseColumnNames: "record_id", baseTableName: "phone_record", constraintName: "FK_lsxgs7the8ax6ts4xrn9c3lgx", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "record", referencesUniqueColumn: "false")
	}

	changeSet(author: "ericbai (generated)", id: "1551375338796-51") {
		addForeignKeyConstraint(baseColumnNames: "share_source_id", baseTableName: "phone_record", constraintName: "FK_6ie3n98wyt9m2nkhkbnvk8w1j", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "phone_record", referencesUniqueColumn: "false")
	}

	changeSet(author: "ericbai (generated)", id: "1551375338796-52") {
		addForeignKeyConstraint(baseColumnNames: "phone_record_id", baseTableName: "phone_record_members_phone_record", constraintName: "FK_bohsc8q4ufjq5t8435gqbwkep", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "phone_record", referencesUniqueColumn: "false")
	}

	changeSet(author: "ericbai (generated)", id: "1551375338796-53") {
		addForeignKeyConstraint(baseColumnNames: "phone_record_members_phone_records_id", baseTableName: "phone_record_members_phone_record", constraintName: "FK_h6f58d9i5m61blvtqyh861j9k", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "phone_record_members", referencesUniqueColumn: "false")
	}
}
