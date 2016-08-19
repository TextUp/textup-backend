import org.textup.Constants

databaseChangeLog = {

	String encodedDefault = ctx.getBean("passwordEncoder")
		.encodePassword(Constants.DEFAULT_LOCK_CODE, null)

	changeSet(author: "eb27 (generated)", id: "1471631452247-1") {
		addColumn(tableName: "staff") {
			column(name: "lock_code", type: "varchar(255)",
				defaultValue:encodedDefault) {
				constraints(nullable: "false")
			}
		}
	}
}
