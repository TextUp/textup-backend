import org.textup.Constants

databaseChangeLog = {
	// bcrypt encoded default is 8888
	String encodedDefault = '$2a$10$TIIecZI1r8Dh8T3Rd4PFnOj21fA94JkOuYi51kH.Wk2KHYNxQL4fu'
	changeSet(author: "eb27 (generated)", id: "1471631452247-1") {
		addColumn(tableName: "staff") {
			column(name: "lock_code", type: "varchar(255)",
				defaultValue:encodedDefault) {
				constraints(nullable: "false")
			}
		}
	}
}
