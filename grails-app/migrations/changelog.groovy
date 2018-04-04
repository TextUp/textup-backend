databaseChangeLog = {

	changeSet(author: "eb27 (generated)", id: "1465832309673-1") {
		createTable(tableName: "announcement_receipt") {
			column(autoIncrement: "true", name: "id", type: "bigint") {
				constraints(nullable: "false", primaryKey: "true", primaryKeyName: "announcement_PK")
			}

			column(name: "version", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "announcement_id", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "session_id", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "type", type: "varchar(255)") {
				constraints(nullable: "false")
			}

			column(name: "when_created", type: "datetime") {
				constraints(nullable: "false")
			}
		}
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-2") {
		createTable(tableName: "contact") {
			column(autoIncrement: "true", name: "id", type: "bigint") {
				constraints(nullable: "false", primaryKey: "true", primaryKeyName: "contactPK")
			}

			column(name: "version", type: "bigint") {
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

			column(name: "status", type: "varchar(255)") {
				constraints(nullable: "false")
			}
		}
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-3") {
		createTable(tableName: "contact_number") {
			column(autoIncrement: "true", name: "id", type: "bigint") {
				constraints(nullable: "false", primaryKey: "true", primaryKeyName: "contact_numbePK")
			}

			column(name: "version", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "number", type: "varchar(255)") {
				constraints(nullable: "false")
			}

			column(name: "owner_id", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "preference", type: "integer") {
				constraints(nullable: "false")
			}

			column(name: "numbers_idx", type: "integer")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-4") {
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

			column(name: "is_deleted", type: "bit") {
				constraints(nullable: "false")
			}

			column(name: "name", type: "varchar(255)") {
				constraints(nullable: "false")
			}

			column(name: "phone_id", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "record_id", type: "bigint") {
				constraints(nullable: "false")
			}
		}
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-5") {
		createTable(tableName: "contact_tag_contact") {
			column(name: "contact_tag_members_id", type: "bigint")

			column(name: "contact_id", type: "bigint")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-6") {
		createTable(tableName: "featured_announcement") {
			column(autoIncrement: "true", name: "id", type: "bigint") {
				constraints(nullable: "false", primaryKey: "true", primaryKeyName: "featured_annoPK")
			}

			column(name: "version", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "expires_at", type: "datetime") {
				constraints(nullable: "false")
			}

			column(name: "message", type: "varchar(255)") {
				constraints(nullable: "false")
			}

			column(name: "owner_id", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "when_created", type: "datetime") {
				constraints(nullable: "false")
			}
		}
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-7") {
		createTable(tableName: "incoming_session") {
			column(autoIncrement: "true", name: "id", type: "bigint") {
				constraints(nullable: "false", primaryKey: "true", primaryKeyName: "incoming_sessPK")
			}

			column(name: "version", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "is_subscribed_to_call", type: "bit") {
				constraints(nullable: "false")
			}

			column(name: "is_subscribed_to_text", type: "bit") {
				constraints(nullable: "false")
			}

			column(name: "last_sent_instructions", type: "datetime") {
				constraints(nullable: "false")
			}

			column(name: "number_as_string", type: "varchar(255)") {
				constraints(nullable: "false")
			}

			column(name: "phone_id", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "when_created", type: "datetime") {
				constraints(nullable: "false")
			}
		}
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-8") {
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

	changeSet(author: "eb27 (generated)", id: "1465832309673-9") {
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

			column(name: "status", type: "varchar(255)") {
				constraints(nullable: "false")
			}
		}
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-10") {
		createTable(tableName: "phone") {
			column(autoIncrement: "true", name: "id", type: "bigint") {
				constraints(nullable: "false", primaryKey: "true", primaryKeyName: "phonePK")
			}

			column(name: "version", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "api_id", type: "varchar(255)")

			column(name: "away_message", type: "varchar(160)") {
				constraints(nullable: "false")
			}

			column(name: "number_as_string", type: "varchar(255)") {
				constraints(nullable: "false")
			}

			column(name: "owner_id", type: "bigint")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-11") {
		createTable(tableName: "phone_ownership") {
			column(autoIncrement: "true", name: "id", type: "bigint") {
				constraints(nullable: "false", primaryKey: "true", primaryKeyName: "phone_ownershPK")
			}

			column(name: "version", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "owner_id", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "phone_id", type: "bigint")

			column(name: "type", type: "varchar(255)") {
				constraints(nullable: "false")
			}
		}
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-12") {
		createTable(tableName: "record") {
			column(autoIncrement: "true", name: "id", type: "bigint") {
				constraints(nullable: "false", primaryKey: "true", primaryKeyName: "recordPK")
			}

			column(name: "version", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "last_record_activity", type: "datetime") {
				constraints(nullable: "false")
			}
		}
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-13") {
		createTable(tableName: "record_item") {
			column(autoIncrement: "true", name: "id", type: "bigint") {
				constraints(nullable: "false", primaryKey: "true", primaryKeyName: "record_itemPK")
			}

			column(name: "version", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "author_id", type: "bigint")

			column(name: "author_name", type: "varchar(255)")

			column(name: "author_type", type: "varchar(255)")

			column(name: "has_away_message", type: "bit") {
				constraints(nullable: "false")
			}

			column(name: "is_announcement", type: "bit") {
				constraints(nullable: "false")
			}

			column(name: "outgoing", type: "bit") {
				constraints(nullable: "false")
			}

			column(name: "record_id", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "when_created", type: "datetime") {
				constraints(nullable: "false")
			}

			column(name: "class", type: "varchar(255)") {
				constraints(nullable: "false")
			}

			column(name: "duration_in_seconds", type: "integer")

			column(name: "voicemail_in_seconds", type: "integer")

			column(name: "contents", type: "varchar(320)")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-14") {
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

			column(name: "received_by_as_string", type: "varchar(255)") {
				constraints(nullable: "false")
			}

			column(name: "status", type: "varchar(255)") {
				constraints(nullable: "false")
			}
		}
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-15") {
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

	changeSet(author: "eb27 (generated)", id: "1465832309673-16") {
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

	changeSet(author: "eb27 (generated)", id: "1465832309673-17") {
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

			column(name: "date_expired", type: "datetime")

			column(name: "permission", type: "varchar(255)") {
				constraints(nullable: "false")
			}

			column(name: "shared_by_id", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "shared_with_id", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "when_created", type: "datetime") {
				constraints(nullable: "false")
			}
		}
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-18") {
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

			column(name: "personal_phone_as_string", type: "varchar(255)")

			column(name: "schedule_id", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "status", type: "varchar(255)") {
				constraints(nullable: "false")
			}

			column(name: "username", type: "varchar(255)") {
				constraints(nullable: "false")
			}
		}
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-19") {
		createTable(tableName: "staff_role") {
			column(name: "role_id", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "staff_id", type: "bigint") {
				constraints(nullable: "false")
			}
		}
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-20") {
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

			column(name: "is_deleted", type: "bit") {
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
		}
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-21") {
		createTable(tableName: "team_staff") {
			column(name: "team_members_id", type: "bigint")

			column(name: "staff_id", type: "bigint")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-22") {
		createTable(tableName: "token") {
			column(autoIncrement: "true", name: "id", type: "bigint") {
				constraints(nullable: "false", primaryKey: "true", primaryKeyName: "tokenPK")
			}

			column(name: "version", type: "bigint") {
				constraints(nullable: "false")
			}

			column(name: "_string_data", type: "varchar(255)") {
				constraints(nullable: "false")
			}

			column(name: "expires", type: "datetime") {
				constraints(nullable: "false")
			}

			column(name: "token", type: "varchar(255)") {
				constraints(nullable: "false")
			}

			column(name: "type", type: "varchar(255)") {
				constraints(nullable: "false")
			}
		}
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-23") {
		addPrimaryKey(columnNames: "role_id, staff_id", constraintName: "staff_rolePK", tableName: "staff_role")
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-51") {
		createIndex(indexName: "FK_eq5oxo21pnjdvr99kyyxdrg6t", tableName: "announcement_receipt") {
			column(name: "session_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-52") {
		createIndex(indexName: "FK_ka3vh5k55arwqksrhb0ga50md", tableName: "announcement_receipt") {
			column(name: "announcement_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-53") {
		createIndex(indexName: "FK_7kcrj4x2fu8y1ixf5ouwiaksw", tableName: "contact") {
			column(name: "record_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-54") {
		createIndex(indexName: "FK_m2nh62y3a1b84ii70ry5teui8", tableName: "contact") {
			column(name: "phone_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-55") {
		createIndex(indexName: "FK_1cjhdw1dw396gqxyeqbv7x863", tableName: "contact_number") {
			column(name: "owner_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-56") {
		createIndex(indexName: "FK_74jr9a2pikdq0v6lv26x480l7", tableName: "contact_tag") {
			column(name: "record_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-57") {
		createIndex(indexName: "FK_omd2pr1ijl9467fgyrg0o414x", tableName: "contact_tag") {
			column(name: "phone_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-58") {
		createIndex(indexName: "FK_hw2mnvgpqrvkwvl92pvfcfhil", tableName: "contact_tag_contact") {
			column(name: "contact_tag_members_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-59") {
		createIndex(indexName: "FK_l0ckitkb43i7hlxaa8ada6xne", tableName: "contact_tag_contact") {
			column(name: "contact_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-60") {
		createIndex(indexName: "FK_9p3ywln6ypt83d9ae79dlsqkn", tableName: "featured_announcement") {
			column(name: "owner_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-61") {
		createIndex(indexName: "FK_7ude3x1fb4was46cv2mcto2x5", tableName: "incoming_session") {
			column(name: "phone_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-62") {
		createIndex(indexName: "FK_gmf1p21c94ksgka76b9ys3skb", tableName: "organization") {
			column(name: "location_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-63") {
		createIndex(indexName: "FK_kmw248yxd9qlhojqx99ky386v", tableName: "phone") {
			column(name: "owner_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-64") {
		createIndex(indexName: "api_id_uniq_1465832309608", tableName: "phone", unique: "true") {
			column(name: "api_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-65") {
		createIndex(indexName: "FK_dlmv9jfo01rgxlbd5enelj5os", tableName: "phone_ownership") {
			column(name: "phone_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-66") {
		createIndex(indexName: "FK_sstg5h8rqp1skuinkp6m5jeh", tableName: "record_item") {
			column(name: "record_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-67") {
		createIndex(indexName: "FK_djuaj2r12gqvltie39wo9lha1", tableName: "record_item_receipt") {
			column(name: "item_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-68") {
		createIndex(indexName: "authority_uniq_1465832309615", tableName: "role", unique: "true") {
			column(name: "authority")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-69") {
		createIndex(indexName: "FK_bnai663rn9nx40gq13m2uyb2n", tableName: "shared_contact") {
			column(name: "contact_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-70") {
		createIndex(indexName: "FK_bqfcufat8ao5c936xdg6wvfo0", tableName: "shared_contact") {
			column(name: "shared_with_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-71") {
		createIndex(indexName: "FK_dr1wogxfb3nutwkcsyo7g92m", tableName: "shared_contact") {
			column(name: "shared_by_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-72") {
		createIndex(indexName: "FK_idvwygj7bloa7mqlwe6tmkpc8", tableName: "staff") {
			column(name: "schedule_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-73") {
		createIndex(indexName: "FK_pk3wl7h24p20antphnvmkp4jq", tableName: "staff") {
			column(name: "org_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-74") {
		createIndex(indexName: "username_uniq_1465832309621", tableName: "staff", unique: "true") {
			column(name: "username")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-75") {
		createIndex(indexName: "FK_3s5lm0tyscx71j9h96ofle9bw", tableName: "staff_role") {
			column(name: "role_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-76") {
		createIndex(indexName: "FK_h1fn2vlhb6p2gp2r6ck1lqhdb", tableName: "staff_role") {
			column(name: "staff_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-77") {
		createIndex(indexName: "FK_ijfygaa92qswc6hmx0yi2hmpp", tableName: "team") {
			column(name: "location_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-78") {
		createIndex(indexName: "FK_kdt7knoc1v1rpl460c64fr74g", tableName: "team") {
			column(name: "org_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-79") {
		createIndex(indexName: "FK_dlhtktc631u2ui5we007uif0g", tableName: "team_staff") {
			column(name: "team_members_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-80") {
		createIndex(indexName: "FK_eijr47qe8mwlsslu54e8wii97", tableName: "team_staff") {
			column(name: "staff_id")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-81") {
		createIndex(indexName: "token_uniq_1465832309623", tableName: "token", unique: "true") {
			column(name: "token")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-24") {
		addForeignKeyConstraint(baseColumnNames: "announcement_id", baseTableName: "announcement_receipt", constraintName: "FK_ka3vh5k55arwqksrhb0ga50md", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "featured_announcement", referencesUniqueColumn: "false")
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-25") {
		addForeignKeyConstraint(baseColumnNames: "session_id", baseTableName: "announcement_receipt", constraintName: "FK_eq5oxo21pnjdvr99kyyxdrg6t", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "incoming_session", referencesUniqueColumn: "false")
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-26") {
		addForeignKeyConstraint(baseColumnNames: "phone_id", baseTableName: "contact", constraintName: "FK_m2nh62y3a1b84ii70ry5teui8", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "phone", referencesUniqueColumn: "false")
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-27") {
		addForeignKeyConstraint(baseColumnNames: "record_id", baseTableName: "contact", constraintName: "FK_7kcrj4x2fu8y1ixf5ouwiaksw", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "record", referencesUniqueColumn: "false")
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-28") {
		addForeignKeyConstraint(baseColumnNames: "owner_id", baseTableName: "contact_number", constraintName: "FK_1cjhdw1dw396gqxyeqbv7x863", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "contact", referencesUniqueColumn: "false")
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-29") {
		addForeignKeyConstraint(baseColumnNames: "phone_id", baseTableName: "contact_tag", constraintName: "FK_omd2pr1ijl9467fgyrg0o414x", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "phone", referencesUniqueColumn: "false")
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-30") {
		addForeignKeyConstraint(baseColumnNames: "record_id", baseTableName: "contact_tag", constraintName: "FK_74jr9a2pikdq0v6lv26x480l7", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "record", referencesUniqueColumn: "false")
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-31") {
		addForeignKeyConstraint(baseColumnNames: "contact_id", baseTableName: "contact_tag_contact", constraintName: "FK_l0ckitkb43i7hlxaa8ada6xne", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "contact", referencesUniqueColumn: "false")
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-32") {
		addForeignKeyConstraint(baseColumnNames: "contact_tag_members_id", baseTableName: "contact_tag_contact", constraintName: "FK_hw2mnvgpqrvkwvl92pvfcfhil", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "contact_tag", referencesUniqueColumn: "false")
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-33") {
		addForeignKeyConstraint(baseColumnNames: "owner_id", baseTableName: "featured_announcement", constraintName: "FK_9p3ywln6ypt83d9ae79dlsqkn", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "phone", referencesUniqueColumn: "false")
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-34") {
		addForeignKeyConstraint(baseColumnNames: "phone_id", baseTableName: "incoming_session", constraintName: "FK_7ude3x1fb4was46cv2mcto2x5", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "phone", referencesUniqueColumn: "false")
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-35") {
		addForeignKeyConstraint(baseColumnNames: "location_id", baseTableName: "organization", constraintName: "FK_gmf1p21c94ksgka76b9ys3skb", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "location", referencesUniqueColumn: "false")
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-36") {
		addForeignKeyConstraint(baseColumnNames: "owner_id", baseTableName: "phone", constraintName: "FK_kmw248yxd9qlhojqx99ky386v", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "phone_ownership", referencesUniqueColumn: "false")
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-37") {
		addForeignKeyConstraint(baseColumnNames: "phone_id", baseTableName: "phone_ownership", constraintName: "FK_dlmv9jfo01rgxlbd5enelj5os", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "phone", referencesUniqueColumn: "false")
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-38") {
		addForeignKeyConstraint(baseColumnNames: "record_id", baseTableName: "record_item", constraintName: "FK_sstg5h8rqp1skuinkp6m5jeh", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "record", referencesUniqueColumn: "false")
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-39") {
		addForeignKeyConstraint(baseColumnNames: "item_id", baseTableName: "record_item_receipt", constraintName: "FK_djuaj2r12gqvltie39wo9lha1", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "record_item", referencesUniqueColumn: "false")
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-40") {
		addForeignKeyConstraint(baseColumnNames: "contact_id", baseTableName: "shared_contact", constraintName: "FK_bnai663rn9nx40gq13m2uyb2n", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "contact", referencesUniqueColumn: "false")
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-41") {
		addForeignKeyConstraint(baseColumnNames: "shared_by_id", baseTableName: "shared_contact", constraintName: "FK_dr1wogxfb3nutwkcsyo7g92m", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "phone", referencesUniqueColumn: "false")
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-42") {
		addForeignKeyConstraint(baseColumnNames: "shared_with_id", baseTableName: "shared_contact", constraintName: "FK_bqfcufat8ao5c936xdg6wvfo0", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "phone", referencesUniqueColumn: "false")
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-43") {
		addForeignKeyConstraint(baseColumnNames: "org_id", baseTableName: "staff", constraintName: "FK_pk3wl7h24p20antphnvmkp4jq", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "organization", referencesUniqueColumn: "false")
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-44") {
		addForeignKeyConstraint(baseColumnNames: "schedule_id", baseTableName: "staff", constraintName: "FK_idvwygj7bloa7mqlwe6tmkpc8", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "schedule", referencesUniqueColumn: "false")
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-45") {
		addForeignKeyConstraint(baseColumnNames: "role_id", baseTableName: "staff_role", constraintName: "FK_3s5lm0tyscx71j9h96ofle9bw", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "role", referencesUniqueColumn: "false")
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-46") {
		addForeignKeyConstraint(baseColumnNames: "staff_id", baseTableName: "staff_role", constraintName: "FK_h1fn2vlhb6p2gp2r6ck1lqhdb", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "staff", referencesUniqueColumn: "false")
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-47") {
		addForeignKeyConstraint(baseColumnNames: "location_id", baseTableName: "team", constraintName: "FK_ijfygaa92qswc6hmx0yi2hmpp", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "location", referencesUniqueColumn: "false")
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-48") {
		addForeignKeyConstraint(baseColumnNames: "org_id", baseTableName: "team", constraintName: "FK_kdt7knoc1v1rpl460c64fr74g", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "organization", referencesUniqueColumn: "false")
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-49") {
		addForeignKeyConstraint(baseColumnNames: "staff_id", baseTableName: "team_staff", constraintName: "FK_eijr47qe8mwlsslu54e8wii97", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "staff", referencesUniqueColumn: "false")
	}

	changeSet(author: "eb27 (generated)", id: "1465832309673-50") {
		addForeignKeyConstraint(baseColumnNames: "team_members_id", baseTableName: "team_staff", constraintName: "FK_dlhtktc631u2ui5we007uif0g", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "team", referencesUniqueColumn: "false")
	}

	include file: 'inactive-phone.groovy'

	include file: 'scheduled-message.groovy'

	include file: 'quartz-innodb.groovy'

	include file: 'lock-code.groovy'

	include file: 'remove-text-length-constraint.groovy'

	include file: 'notifications.groovy'

	include file: 'remove-token-data-length-constraint.groovy'

	include file: 'record-note.groovy'

	include file: 'lock-timeout.groovy'

	include file: 'notification-policy.groovy'

	include file: 'phone-voice.groovy'

	include file: 'shared-contact-separate-status.groovy'

	include file: 'future-message-daylight-savings-adjustment.groovy'

	include file: 'domain-timestamps.groovy'

	include file: 'voice-language.groovy'
}
