databaseChangeLog = {

	changeSet(author: "eb27 (generated)", id: "1450588128620-1") {
		createTable(tableName: "client_session") {
			column(autoIncrement: "true", name: "id", type: "bigint") {
				constraints(nullable: "false", primaryKey: "true", primaryKeyName: "client_sessioPK")
			}

			column(name: "version", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "last_sent_instructions", type: "datetime") {
				constraints(nullable: "false")
			}

			column(name: "most_recent_tag_id", type: "bigint")

			column(name: "number_number", type: "varchar(255)") {
				constraints(nullable: "false")
			}

			column(name: "team_phone_id", type: "bigint") {
				constraints(nullable: "false")
			}
		}
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-2") {
		createTable(tableName: "contact") {
			column(autoIncrement: "true", name: "id", type: "bigint") {
				constraints(nullable: "false", primaryKey: "true", primaryKeyName: "contactPK")
			}

			column(name: "version", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "last_record_activity", type: "datetime") {
				constraints(nullable: "false")
			}

			column(name: "name", type: "varchar(255)")

			column(name: "note", type: "varchar(1000)")

			column(name: "phone_id", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "record_id", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "status", type: "varchar(8)") {
				constraints(nullable: "false")
			}
		}
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-3") {
		createTable(tableName: "contact_tag") {
			column(autoIncrement: "true", name: "id", type: "bigint") {
				constraints(nullable: "false", primaryKey: "true", primaryKeyName: "contact_tagPK")
			}

			column(name: "version", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "hex_color", type: "varchar(255)") {
				constraints(nullable: "false")
			}

			column(name: "name", type: "varchar(255)") {
				constraints(nullable: "false")
			}

			column(name: "phone_id", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "class", type: "varchar(255)") {
				constraints(nullable: "false")
			}

			column(name: "last_record_activity", type: "datetime")

			column(name: "record_id", type: "bigint")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-4") {
		createTable(tableName: "featured_announcement") {
			column(autoIncrement: "true", name: "id", type: "bigint") {
				constraints(nullable: "false", primaryKey: "true", primaryKeyName: "featured_annoPK")
			}

			column(name: "version", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "date_created", type: "datetime") {
				constraints(nullable: "false")
			}

			column(name: "expires_at", type: "datetime") {
				constraints(nullable: "false")
			}

			column(name: "featured_id", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "owner_id", type: "bigint") {
				constraints(nullable: "false")
			}
		}
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-5") {
		createTable(tableName: "location") {
			column(autoIncrement: "true", name: "id", type: "bigint") {
				constraints(nullable: "false", primaryKey: "true", primaryKeyName: "locationPK")
			}

			column(name: "version", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "address", type: "varchar(255)") {
				constraints(nullable: "false")
			}

			column(name: "lat", type: "decimal(19,2)") {
				constraints(nullable: "false")
			}

			column(name: "lon", type: "decimal(19,2)") {
				constraints(nullable: "false")
			}
		}
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-6") {
		createTable(tableName: "organization") {
			column(autoIncrement: "true", name: "id", type: "bigint") {
				constraints(nullable: "false", primaryKey: "true", primaryKeyName: "organizationPK")
			}

			column(name: "version", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "location_id", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "name", type: "varchar(255)") {
				constraints(nullable: "false")
			}

			column(name: "status", type: "varchar(8)") {
				constraints(nullable: "false")
			}
		}
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-7") {
		createTable(tableName: "password_reset_token") {
			column(autoIncrement: "true", name: "id", type: "bigint") {
				constraints(nullable: "false", primaryKey: "true", primaryKeyName: "password_resePK")
			}

			column(name: "version", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "expires", type: "datetime") {
				constraints(nullable: "false")
			}

			column(name: "to_be_reset_id", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "token", type: "varchar(255)") {
				constraints(nullable: "false")
			}
		}
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-8") {
		createTable(tableName: "phone") {
			column(autoIncrement: "true", name: "id", type: "bigint") {
				constraints(nullable: "false", primaryKey: "true", primaryKeyName: "phonePK")
			}

			column(name: "version", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "api_id", type: "varchar(255)")

			column(name: "number_number", type: "varchar(255)") {
				constraints(nullable: "false")
			}

			column(name: "class", type: "varchar(255)") {
				constraints(nullable: "false")
			}

			column(name: "owner_id", type: "bigint")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-9") {
		createTable(tableName: "phone_number") {
			column(autoIncrement: "true", name: "id", type: "bigint") {
				constraints(nullable: "false", primaryKey: "true", primaryKeyName: "phone_numberPK")
			}

			column(name: "version", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "number", type: "varchar(255)") {
				constraints(nullable: "false")
			}

			column(name: "class", type: "varchar(255)") {
				constraints(nullable: "false")
			}

			column(name: "contact_id", type: "bigint")

			column(name: "owner_id", type: "bigint")

			column(name: "preference", type: "integer")

			column(name: "numbers_idx", type: "integer")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-10") {
		createTable(tableName: "record") {
			column(autoIncrement: "true", name: "id", type: "bigint") {
				constraints(nullable: "false", primaryKey: "true", primaryKeyName: "recordPK")
			}

			column(name: "version", type: "bigint") {
				constraints(nullable: "false")
			}
		}
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-11") {
		createTable(tableName: "record_item") {
			column(autoIncrement: "true", name: "id", type: "bigint") {
				constraints(nullable: "false", primaryKey: "true", primaryKeyName: "record_itemPK")
			}

			column(name: "version", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "author_id", type: "bigint")

			column(name: "author_name", type: "varchar(255)")

			column(name: "date_created", type: "datetime") {
				constraints(nullable: "false")
			}

			column(name: "outgoing", type: "bit") {
				constraints(nullable: "false")
			}

			column(name: "record_id", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "class", type: "varchar(255)") {
				constraints(nullable: "false")
			}

			column(name: "duration_in_seconds", type: "integer")

			column(name: "has_voicemail", type: "bit")

			column(name: "voicemail_in_seconds", type: "integer")

			column(name: "editable", type: "bit")

			column(name: "note", type: "varchar(250)")

			column(name: "contents", type: "varchar(320)")

			column(name: "future_text", type: "bit")

			column(name: "send_at", type: "datetime")

			column(name: "team_contact_tag_id", type: "bigint")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-12") {
		createTable(tableName: "record_item_receipt") {
			column(autoIncrement: "true", name: "id", type: "bigint") {
				constraints(nullable: "false", primaryKey: "true", primaryKeyName: "record_item_rPK")
			}

			column(name: "version", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "api_id", type: "varchar(255)") {
				constraints(nullable: "false")
			}

			column(name: "item_id", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "received_by_number", type: "varchar(255)") {
				constraints(nullable: "false")
			}

			column(name: "status", type: "varchar(7)") {
				constraints(nullable: "false")
			}
		}
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-13") {
		createTable(tableName: "role") {
			column(autoIncrement: "true", name: "id", type: "bigint") {
				constraints(nullable: "false", primaryKey: "true", primaryKeyName: "rolePK")
			}

			column(name: "version", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "authority", type: "varchar(255)") {
				constraints(nullable: "false")
			}
		}
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-14") {
		createTable(tableName: "schedule") {
			column(autoIncrement: "true", name: "id", type: "bigint") {
				constraints(nullable: "false", primaryKey: "true", primaryKeyName: "schedulePK")
			}

			column(name: "version", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "class", type: "varchar(255)") {
				constraints(nullable: "false")
			}

			column(name: "friday", type: "varchar(255)")

			column(name: "monday", type: "varchar(255)")

			column(name: "saturday", type: "varchar(255)")

			column(name: "sunday", type: "varchar(255)")

			column(name: "thursday", type: "varchar(255)")

			column(name: "tuesday", type: "varchar(255)")

			column(name: "wednesday", type: "varchar(255)")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-15") {
		createTable(tableName: "shared_contact") {
			column(autoIncrement: "true", name: "id", type: "bigint") {
				constraints(nullable: "false", primaryKey: "true", primaryKeyName: "shared_contacPK")
			}

			column(name: "version", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "contact_id", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "date_created", type: "datetime") {
				constraints(nullable: "false")
			}

			column(name: "date_expired", type: "datetime")

			column(name: "permission", type: "varchar(8)") {
				constraints(nullable: "false")
			}

			column(name: "shared_by_id", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "shared_with_id", type: "bigint") {
				constraints(nullable: "false")
			}
		}
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-16") {
		createTable(tableName: "staff") {
			column(autoIncrement: "true", name: "id", type: "bigint") {
				constraints(nullable: "false", primaryKey: "true", primaryKeyName: "staffPK")
			}

			column(name: "version", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "account_expired", type: "bit") {
				constraints(nullable: "false")
			}

			column(name: "account_locked", type: "bit") {
				constraints(nullable: "false")
			}

			column(name: "away_message", type: "varchar(160)") {
				constraints(nullable: "false")
			}

			column(name: "email", type: "varchar(255)") {
				constraints(nullable: "false")
			}

			column(name: "enabled", type: "bit") {
				constraints(nullable: "false")
			}

			column(name: "is_available", type: "bit") {
				constraints(nullable: "false")
			}

			column(name: "manual_schedule", type: "bit") {
				constraints(nullable: "false")
			}

			column(name: "name", type: "varchar(255)") {
				constraints(nullable: "false")
			}

			column(name: "org_id", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "password", type: "varchar(255)") {
				constraints(nullable: "false")
			}

			column(name: "password_expired", type: "bit") {
				constraints(nullable: "false")
			}

			column(name: "personal_phone_number_number", type: "varchar(255)")

			column(name: "phone_id", type: "bigint")

			column(name: "schedule_id", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "status", type: "varchar(7)") {
				constraints(nullable: "false")
			}

			column(name: "username", type: "varchar(255)") {
				constraints(nullable: "false")
			}
		}
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-17") {
		createTable(tableName: "staff_role") {
			column(name: "role_id", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "staff_id", type: "bigint") {
				constraints(nullable: "false")
			}
		}
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-18") {
		createTable(tableName: "tag_membership") {
			column(autoIncrement: "true", name: "id", type: "bigint") {
				constraints(nullable: "false", primaryKey: "true", primaryKeyName: "tag_membershiPK")
			}

			column(name: "version", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "contact_id", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "has_unsubscribed", type: "bit") {
				constraints(nullable: "false")
			}

			column(name: "subscription_type", type: "varchar(4)")

			column(name: "tag_id", type: "bigint") {
				constraints(nullable: "false")
			}
		}
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-19") {
		createTable(tableName: "team") {
			column(autoIncrement: "true", name: "id", type: "bigint") {
				constraints(nullable: "false", primaryKey: "true", primaryKeyName: "teamPK")
			}

			column(name: "version", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "hex_color", type: "varchar(255)") {
				constraints(nullable: "false")
			}

			column(name: "location_id", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "name", type: "varchar(255)") {
				constraints(nullable: "false")
			}

			column(name: "org_id", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "phone_id", type: "bigint")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-20") {
		createTable(tableName: "team_membership") {
			column(autoIncrement: "true", name: "id", type: "bigint") {
				constraints(nullable: "false", primaryKey: "true", primaryKeyName: "team_membershPK")
			}

			column(name: "version", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "staff_id", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "team_id", type: "bigint") {
				constraints(nullable: "false")
			}
		}
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-21") {
		addPrimaryKey(columnNames: "role_id, staff_id", constraintName: "staff_rolePK", tableName: "staff_role")
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-48") {
		createIndex(indexName: "FK_67thxe3eo1vrn1ev4qjeom2jl", tableName: "client_session") {
			column(name: "team_phone_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-49") {
		createIndex(indexName: "FK_7kcrj4x2fu8y1ixf5ouwiaksw", tableName: "contact") {
			column(name: "record_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-50") {
		createIndex(indexName: "FK_m2nh62y3a1b84ii70ry5teui8", tableName: "contact") {
			column(name: "phone_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-51") {
		createIndex(indexName: "FK_74jr9a2pikdq0v6lv26x480l7", tableName: "contact_tag") {
			column(name: "record_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-52") {
		createIndex(indexName: "FK_omd2pr1ijl9467fgyrg0o414x", tableName: "contact_tag") {
			column(name: "phone_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-53") {
		createIndex(indexName: "FK_7txl5tqnot6whjx159e1awk2f", tableName: "featured_announcement") {
			column(name: "featured_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-54") {
		createIndex(indexName: "FK_9p3ywln6ypt83d9ae79dlsqkn", tableName: "featured_announcement") {
			column(name: "owner_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-55") {
		createIndex(indexName: "FK_gmf1p21c94ksgka76b9ys3skb", tableName: "organization") {
			column(name: "location_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-56") {
		createIndex(indexName: "token_uniq_1450588128553", tableName: "password_reset_token", unique: "true") {
			column(name: "token")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-57") {
		createIndex(indexName: "api_id_uniq_1450588128556", tableName: "phone", unique: "true") {
			column(name: "api_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-58") {
		createIndex(indexName: "FK_d42udkbsxihesdrga6hvqqqn2", tableName: "phone_number") {
			column(name: "contact_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-59") {
		createIndex(indexName: "FK_sstg5h8rqp1skuinkp6m5jeh", tableName: "record_item") {
			column(name: "record_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-60") {
		createIndex(indexName: "FK_djuaj2r12gqvltie39wo9lha1", tableName: "record_item_receipt") {
			column(name: "item_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-61") {
		createIndex(indexName: "authority_uniq_1450588128563", tableName: "role", unique: "true") {
			column(name: "authority")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-62") {
		createIndex(indexName: "FK_bnai663rn9nx40gq13m2uyb2n", tableName: "shared_contact") {
			column(name: "contact_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-63") {
		createIndex(indexName: "FK_bqfcufat8ao5c936xdg6wvfo0", tableName: "shared_contact") {
			column(name: "shared_with_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-64") {
		createIndex(indexName: "FK_dr1wogxfb3nutwkcsyo7g92m", tableName: "shared_contact") {
			column(name: "shared_by_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-65") {
		createIndex(indexName: "FK_idvwygj7bloa7mqlwe6tmkpc8", tableName: "staff") {
			column(name: "schedule_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-66") {
		createIndex(indexName: "FK_jr45iplkb61wahren41vstgsg", tableName: "staff") {
			column(name: "phone_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-67") {
		createIndex(indexName: "FK_pk3wl7h24p20antphnvmkp4jq", tableName: "staff") {
			column(name: "org_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-68") {
		createIndex(indexName: "username_uniq_1450588128570", tableName: "staff", unique: "true") {
			column(name: "username")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-69") {
		createIndex(indexName: "FK_3s5lm0tyscx71j9h96ofle9bw", tableName: "staff_role") {
			column(name: "role_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-70") {
		createIndex(indexName: "FK_h1fn2vlhb6p2gp2r6ck1lqhdb", tableName: "staff_role") {
			column(name: "staff_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-71") {
		createIndex(indexName: "FK_8ss5v774d7jyt0jvlks2ljwwo", tableName: "tag_membership") {
			column(name: "contact_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-72") {
		createIndex(indexName: "FK_tm0vfnvwxahemgylx4y4ksecg", tableName: "tag_membership") {
			column(name: "tag_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-73") {
		createIndex(indexName: "FK_5033b3wmmh7k2l2y9trlv2suh", tableName: "team") {
			column(name: "phone_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-74") {
		createIndex(indexName: "FK_ijfygaa92qswc6hmx0yi2hmpp", tableName: "team") {
			column(name: "location_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-75") {
		createIndex(indexName: "FK_kdt7knoc1v1rpl460c64fr74g", tableName: "team") {
			column(name: "org_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-76") {
		createIndex(indexName: "FK_8se7hj1apeqdf6hn78o5y9wxk", tableName: "team_membership") {
			column(name: "staff_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-77") {
		createIndex(indexName: "FK_o1p3a05da825uxnul0w2994t6", tableName: "team_membership") {
			column(name: "team_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-22") {
		addForeignKeyConstraint(baseColumnNames: "team_phone_id", baseTableName: "client_session", constraintName: "FK_67thxe3eo1vrn1ev4qjeom2jl", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "phone", referencesUniqueColumn: "false")
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-23") {
		addForeignKeyConstraint(baseColumnNames: "phone_id", baseTableName: "contact", constraintName: "FK_m2nh62y3a1b84ii70ry5teui8", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "phone", referencesUniqueColumn: "false")
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-24") {
		addForeignKeyConstraint(baseColumnNames: "record_id", baseTableName: "contact", constraintName: "FK_7kcrj4x2fu8y1ixf5ouwiaksw", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "record", referencesUniqueColumn: "false")
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-25") {
		addForeignKeyConstraint(baseColumnNames: "phone_id", baseTableName: "contact_tag", constraintName: "FK_omd2pr1ijl9467fgyrg0o414x", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "phone", referencesUniqueColumn: "false")
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-26") {
		addForeignKeyConstraint(baseColumnNames: "record_id", baseTableName: "contact_tag", constraintName: "FK_74jr9a2pikdq0v6lv26x480l7", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "record", referencesUniqueColumn: "false")
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-27") {
		addForeignKeyConstraint(baseColumnNames: "featured_id", baseTableName: "featured_announcement", constraintName: "FK_7txl5tqnot6whjx159e1awk2f", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "record_item", referencesUniqueColumn: "false")
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-28") {
		addForeignKeyConstraint(baseColumnNames: "owner_id", baseTableName: "featured_announcement", constraintName: "FK_9p3ywln6ypt83d9ae79dlsqkn", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "phone", referencesUniqueColumn: "false")
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-29") {
		addForeignKeyConstraint(baseColumnNames: "location_id", baseTableName: "organization", constraintName: "FK_gmf1p21c94ksgka76b9ys3skb", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "location", referencesUniqueColumn: "false")
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-30") {
		addForeignKeyConstraint(baseColumnNames: "contact_id", baseTableName: "phone_number", constraintName: "FK_d42udkbsxihesdrga6hvqqqn2", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "contact", referencesUniqueColumn: "false")
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-31") {
		addForeignKeyConstraint(baseColumnNames: "record_id", baseTableName: "record_item", constraintName: "FK_sstg5h8rqp1skuinkp6m5jeh", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "record", referencesUniqueColumn: "false")
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-32") {
		addForeignKeyConstraint(baseColumnNames: "item_id", baseTableName: "record_item_receipt", constraintName: "FK_djuaj2r12gqvltie39wo9lha1", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "record_item", referencesUniqueColumn: "false")
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-33") {
		addForeignKeyConstraint(baseColumnNames: "contact_id", baseTableName: "shared_contact", constraintName: "FK_bnai663rn9nx40gq13m2uyb2n", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "contact", referencesUniqueColumn: "false")
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-34") {
		addForeignKeyConstraint(baseColumnNames: "shared_by_id", baseTableName: "shared_contact", constraintName: "FK_dr1wogxfb3nutwkcsyo7g92m", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "phone", referencesUniqueColumn: "false")
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-35") {
		addForeignKeyConstraint(baseColumnNames: "shared_with_id", baseTableName: "shared_contact", constraintName: "FK_bqfcufat8ao5c936xdg6wvfo0", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "phone", referencesUniqueColumn: "false")
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-36") {
		addForeignKeyConstraint(baseColumnNames: "org_id", baseTableName: "staff", constraintName: "FK_pk3wl7h24p20antphnvmkp4jq", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "organization", referencesUniqueColumn: "false")
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-37") {
		addForeignKeyConstraint(baseColumnNames: "phone_id", baseTableName: "staff", constraintName: "FK_jr45iplkb61wahren41vstgsg", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "phone", referencesUniqueColumn: "false")
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-38") {
		addForeignKeyConstraint(baseColumnNames: "schedule_id", baseTableName: "staff", constraintName: "FK_idvwygj7bloa7mqlwe6tmkpc8", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "schedule", referencesUniqueColumn: "false")
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-39") {
		addForeignKeyConstraint(baseColumnNames: "role_id", baseTableName: "staff_role", constraintName: "FK_3s5lm0tyscx71j9h96ofle9bw", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "role", referencesUniqueColumn: "false")
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-40") {
		addForeignKeyConstraint(baseColumnNames: "staff_id", baseTableName: "staff_role", constraintName: "FK_h1fn2vlhb6p2gp2r6ck1lqhdb", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "staff", referencesUniqueColumn: "false")
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-41") {
		addForeignKeyConstraint(baseColumnNames: "contact_id", baseTableName: "tag_membership", constraintName: "FK_8ss5v774d7jyt0jvlks2ljwwo", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "contact", referencesUniqueColumn: "false")
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-42") {
		addForeignKeyConstraint(baseColumnNames: "tag_id", baseTableName: "tag_membership", constraintName: "FK_tm0vfnvwxahemgylx4y4ksecg", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "contact_tag", referencesUniqueColumn: "false")
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-43") {
		addForeignKeyConstraint(baseColumnNames: "location_id", baseTableName: "team", constraintName: "FK_ijfygaa92qswc6hmx0yi2hmpp", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "location", referencesUniqueColumn: "false")
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-44") {
		addForeignKeyConstraint(baseColumnNames: "org_id", baseTableName: "team", constraintName: "FK_kdt7knoc1v1rpl460c64fr74g", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "organization", referencesUniqueColumn: "false")
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-45") {
		addForeignKeyConstraint(baseColumnNames: "phone_id", baseTableName: "team", constraintName: "FK_5033b3wmmh7k2l2y9trlv2suh", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "phone", referencesUniqueColumn: "false")
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-46") {
		addForeignKeyConstraint(baseColumnNames: "staff_id", baseTableName: "team_membership", constraintName: "FK_8se7hj1apeqdf6hn78o5y9wxk", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "staff", referencesUniqueColumn: "false")
	}

	changeSet(author: "eb27 (generated)", id: "1450588128620-47") {
		addForeignKeyConstraint(baseColumnNames: "team_id", baseTableName: "team_membership", constraintName: "FK_o1p3a05da825uxnul0w2994t6", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "team", referencesUniqueColumn: "false")
	}
}
