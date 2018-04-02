import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.format.DateTimeFormat

databaseChangeLog = {

	DateTime dt1 = DateTime.now(DateTimeZone.UTC)
	String defaultNow = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss").print(dt1)

	changeSet(author: "ericbai (generated)", id: "1522633873471-1") {
		addColumn(tableName: "contact") {
			column(name: "when_created", type: "datetime", defaultValue:defaultNow) {
				constraints(nullable: "false")
			}
		}
	}

	changeSet(author: "ericbai (generated)", id: "1522633873471-2") {
		addColumn(tableName: "contact_tag") {
			column(name: "when_created", type: "datetime", defaultValue:defaultNow) {
				constraints(nullable: "false")
			}
		}
	}

	changeSet(author: "ericbai (generated)", id: "1522633873471-3") {
		addColumn(tableName: "organization") {
			column(name: "when_created", type: "datetime", defaultValue:defaultNow) {
				constraints(nullable: "false")
			}
		}
	}

	changeSet(author: "ericbai (generated)", id: "1522633873471-4") {
		addColumn(tableName: "phone") {
			column(name: "when_created", type: "datetime", defaultValue:defaultNow) {
				constraints(nullable: "false")
			}
		}
	}

	changeSet(author: "ericbai (generated)", id: "1522633873471-5") {
		addColumn(tableName: "staff") {
			column(name: "when_created", type: "datetime", defaultValue:defaultNow) {
				constraints(nullable: "false")
			}
		}
	}

	changeSet(author: "ericbai (generated)", id: "1522633873471-6") {
		addColumn(tableName: "team") {
			column(name: "when_created", type: "datetime", defaultValue:defaultNow) {
				constraints(nullable: "false")
			}
		}
	}
}
