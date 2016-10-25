databaseChangeLog = {

	changeSet(author: "eb27 (generated)", id: "1477356589404-1") {
		addColumn(tableName: "token") {
			column(name: "max_num_access", type: "integer")
		}
	}

	changeSet(author: "eb27 (generated)", id: "1477356589404-2") {
		renameColumn(tableName: "token",
			oldColumnName: "_string_data",
			newColumnName: "string_data",
			columnDataType: "varchar(255)")
	}

	changeSet(author: "eb27 (generated)", id: "1477356589404-3") {
		addColumn(tableName: "token") {
			column(name: "times_accessed", type: "integer") {
				constraints(nullable: "false")
			}
		}
	}
}
