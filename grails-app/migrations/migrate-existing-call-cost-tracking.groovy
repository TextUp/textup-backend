import groovy.sql.*
import org.textup.*

databaseChangeLog = {

	changeSet(author: "ericbai", id: "1537451166858-1") {
		grailsChange {
			change {
				sql.execute("""
					ALTER TABLE record_item
					ADD COLUMN voicemail_key VARCHAR(255) NULL;
				""")
				// See http://www.mysqltutorial.org/mysql-max-function/
				// for finding the maximum within each group
				sql.executeUpdate("""
					UPDATE record_item AS i
					INNER JOIN (
							SELECT item_id, api_id, MAX(rir.num_billable)
							FROM record_item_receipt AS rir
							WHERE rir.status = "SUCCESS"
							GROUP BY rir.item_id
							ORDER BY MAX(rir.num_billable)
						) AS source
						ON source.item_id = i.id
					SET i.voicemail_key = source.api_id
					WHERE i.class = "org.textup.RecordCall"
						AND i.voicemail_in_seconds > 0
						AND i.has_away_message = TRUE;
				""")
			}
			rollback {
				sql.execute("""
					ALTER TABLE record_item
					DROP COLUMN voicemail_key;
				""")
			}
		}
	}

	changeSet(author: "ericbai", id: "1537451166858-3") {
		grailsChange {
			change {
				sql.executeUpdate("""
					UPDATE record_item_receipt AS rir
					INNER JOIN (
							SELECT id, duration_in_seconds
							FROM record_item AS i
							WHERE i.class = "org.textup.RecordCall"
						) AS source
						ON source.id = rir.item_id
					INNER JOIN (
							SELECT id, item_id, MAX(num_billable)
							FROM record_item_receipt
							GROUP BY item_id
							ORDER BY MAX(num_billable)
						) AS rir2
						ON rir.id = rir2.id
					SET rir.num_billable = source.duration_in_seconds;
				""")
				sql.execute("""
					ALTER TABLE record_item
					DROP COLUMN duration_in_seconds;
				""")
			}
			rollback {
				sql.execute("""
					ALTER TABLE record_item
					ADD COLUMN duration_in_seconds INT NULL;
				""")
				sql.executeUpdate("""
					UPDATE record_item AS i
					INNER JOIN (
							SELECT item_id, num_billable, MAX(num_billable)
							FROM record_item_receipt AS rir
							GROUP BY item_id
							ORDER BY MAX(num_billable)
						) AS source
						ON source.item_id = i.id
					SET i.duration_in_seconds = source.num_billable
					WHERE i.class = "org.textup.RecordCall";
				""")
			}
		}
	}
}
