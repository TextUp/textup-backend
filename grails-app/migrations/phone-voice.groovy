databaseChangeLog = {

	changeSet(author: "ericbai (generated)", id: "1520385235492-1") {
		addColumn(tableName: "phone") {
			column(name: "voice", type: "varchar(255)", defaultValue: VoiceType.MALE.toString()) {
				constraints(nullable: "false")
			}
		}
	}
}
