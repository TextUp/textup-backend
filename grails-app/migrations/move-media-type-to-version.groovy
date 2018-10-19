
// [NOTE] Important that this changelog comes AFTER `asynchronous-media-processing.groovy` because
// we assume here that we have already renamed display_versions to alternate_versions

databaseChangeLog = {

	changeSet(author: "ericbai", id: "1539956368803-13") {
		grailsChange {
			change {
				// step 1: add column to media_element_version
				sql.execute("""
					ALTER TABLE media_element_version
					ADD COLUMN type VARCHAR(255) NOT NULL
				""")
				// step 2: copy data media_element -> media_element_version for alternate versions
				sql.executeUpdate("""
					UPDATE media_element_version AS v
					INNER JOIN (
							SELECT e.type, join_table.media_element_version_id
							FROM media_element_media_element_version AS join_table
							INNER JOIN media_element AS e ON e.id = join_table.media_element_alternate_versions_id
						) as element
						ON element.media_element_version_id = v.id
					SET v.type = element.type
				""")
				// step 3: copy data media_element -> media_element_version for send versions
				sql.executeUpdate("""
					UPDATE media_element_version AS v
					INNER JOIN media_element AS e ON e.send_version_id = v.id
					SET v.type = e.type
				""")
				// step 4: drop column in media_element
				sql.execute("""
					ALTER TABLE media_element
					DROP COLUMN type
				""")
			}
			rollback {
				// step 1: add column to media_element
				sql.execute("""
					ALTER TABLE media_element
					ADD COLUMN type VARCHAR(255) NOT NULL
				""")
				// step 2: copy data media_element_version that is the SEND version -> media_element
				sql.executeUpdate("""
					UPDATE media_element AS e
					INNER JOIN media_element_version AS v ON v.id = e.send_version_id
					SET e.type = v.type
				""")
				// step 3: drop column in media_element_version
				sql.execute("""
					ALTER TABLE media_element_version
					DROP COLUMN type
				""")
			}
		}
	}
}
