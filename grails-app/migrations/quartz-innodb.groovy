databaseChangeLog = {
	changeSet(author: "eb27", id: "quart-innodb-1") {
		sqlFile(path: "sql/quartz-create-mysql-innodb.sql")
		rollback {
			sqlFile(path: "sql/quartz-rollback-mysql-innodb.sql")
		}
	}
}
