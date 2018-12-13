import org.textup.*

databaseChangeLog = {

	changeSet(author: "ericbai (generated)", id: "1544452854815-1") {
		addColumn(tableName: "organization") {
			column(name: "away_message_suffix", type: "varchar(159)",
                defaultValue: Constants.DEFAULT_AWAY_MESSAGE_SUFFIX)
		}
	}

    // remove the existing suffix added to the away message column in the phone table now that we
    // separately store this suffix in the organization table
    changeSet(author: "ericbai", id: "1544452854815-2") {
        grailsChange {
            change {
                sql.executeUpdate("""
                    UPDATE phone
                    SET away_message = REPLACE(away_message, '${Constants.DEFAULT_AWAY_MESSAGE_SUFFIX}', '')
                    WHERE away_message LIKE '%${Constants.DEFAULT_AWAY_MESSAGE_SUFFIX}%'
                """.toString())
            }
        }
    }

    // need to change the column type for the phone.away_message to reflect the larger allowed size
    // the larger size is two text messages = 160 * 2 = 320
    changeSet(author: "ericbai", id: "1544452854815-3") {
        modifyDataType(columnName: "away_message", newDataType: "varchar(320)", tableName: "phone")
    }
}
