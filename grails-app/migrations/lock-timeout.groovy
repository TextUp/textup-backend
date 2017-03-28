import org.textup.Constants

databaseChangeLog = {

	changeSet(author: "eb27 (generated)", id: "1490659343882-2") {
		addColumn(tableName: "organization") {
			column(name: "timeout", type: "integer",
				defaultValue:Constants.DEFAULT_LOCK_TIMEOUT_MILLIS) {
				constraints(nullable: "false")
			}
		}
	}
}
