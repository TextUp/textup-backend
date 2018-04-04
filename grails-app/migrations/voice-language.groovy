import org.textup.type.VoiceLanguage

databaseChangeLog = {

	changeSet(author: "ericbai (generated)", id: "1522815624665-1") {
		addColumn(tableName: "future_message") {
			column(name: "language", type: "varchar(255)", defaultValue: VoiceLanguage.ENGLISH.toString()) {
				constraints(nullable: "false")
			}
		}
	}

	changeSet(author: "ericbai (generated)", id: "1522815624665-2") {
		addColumn(tableName: "phone") {
			column(name: "language", type: "varchar(255)", defaultValue: VoiceLanguage.ENGLISH.toString()) {
				constraints(nullable: "false")
			}
		}
	}

	changeSet(author: "ericbai (generated)", id: "1522815624665-3") {
		addColumn(tableName: "record") {
			column(name: "language", type: "varchar(255)", defaultValue: VoiceLanguage.ENGLISH.toString()) {
				constraints(nullable: "false")
			}
		}
	}
}
