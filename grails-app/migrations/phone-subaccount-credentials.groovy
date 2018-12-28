databaseChangeLog = {

	changeSet(author: "ericbai (generated)", id: "1545968025657-1") {
		createTable(tableName: "custom_account_details") {
			column(autoIncrement: "true", name: "id", type: "bigint") {
				constraints(nullable: "false", primaryKey: "true", primaryKeyName: "custom_accounPK")
			}

			column(name: "version", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "account_id", type: "varchar(255)") {
				constraints(nullable: "false")
			}

			column(name: "auth_token", type: "varchar(255)") {
				constraints(nullable: "false")
			}
		}
	}

	changeSet(author: "ericbai (generated)", id: "1545968025657-2") {
		modifyDataType(columnName: "custom_account_id", newDataType: "bigint", tableName: "phone")
	}

	changeSet(author: "ericbai (generated)", id: "1545968025657-4") {
		createIndex(indexName: "account_id_uniq_1545968024943", tableName: "custom_account_details", unique: "true") {
			column(name: "account_id")
		}
	}

	changeSet(author: "ericbai (generated)", id: "1545968025657-5") {
		createIndex(indexName: "FK_g9fqbx0vnpdvwkfdjy9go6iwy", tableName: "phone") {
			column(name: "custom_account_id")
		}
	}

	changeSet(author: "ericbai (generated)", id: "1545968025657-3") {
		addForeignKeyConstraint(baseColumnNames: "custom_account_id", baseTableName: "phone", constraintName: "FK_g9fqbx0vnpdvwkfdjy9go6iwy", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "custom_account_details", referencesUniqueColumn: "false")
	}
}
