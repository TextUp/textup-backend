import groovy.sql.*
import org.textup.type.MediaType

// [NOTE] that this comes BEFORE `recorded_voicemail_greeting.groovy` so that the `is_public`
// column in the `media_element_version` table doesn't exist yet

databaseChangeLog = {

    // Needs to be a static value so that the checksums remain consistent
    String defaultNow = "2018-10-20 03:22:17"

    changeSet(author: "ericbai", id: "1539956368803-16") {
        grailsChange {
            change {
                // step 1: create a media info if not existing for record item with voicemail_key
                List<GroovyRowResult> existingVoicemailsWithoutMedia = sql.rows("""
                    SELECT id
                    FROM record_item
                    WHERE voicemail_key IS NOT NULL
                        AND media_id IS NULL
                """)
                existingVoicemailsWithoutMedia.each { GroovyRowResult row ->
                    Long mInfoId = sql.executeInsert([version: 0], """
                        INSERT INTO media_info (version)
                        VALUES (:version)
                    """)[0][0]
                    // associate media info with owner
                    sql.executeUpdate([mInfoId: mInfoId, itemId: row.id], """
                        UPDATE record_item
                        SET media_id = :mInfoId
                        WHERE id = :itemId
                    """)
                }
                // Now that we have ensured that all voicemails have an associated media_info...
                List<GroovyRowResult> voicemails = sql.rows("""
                    SELECT voicemail_key, media_id
                    FROM record_item
                    WHERE voicemail_key IS NOT NULL
                """)
                voicemails.each { GroovyRowResult row ->
                    // step 2: create a media element version of type AUDIO_MP3 and copy the
                    // voicemail_key over from record_item to this table as the version_id
                    Long sendVersionId = sql.executeInsert([
                            version: 0,
                            version_id: row.voicemail_key,
                            type: MediaType.AUDIO_MP3.toString(),
                            // we don't know the size of the voicemail recording in bytes so this is our placeholder size value
                            size_in_bytes: 100
                        ], """
                        INSERT INTO media_element_version (
                            version,
                            version_id,
                            type,
                            size_in_bytes)
                        VALUES (
                            :version,
                            :version_id,
                            :type,
                            :size_in_bytes)
                    """)[0][0]
                    // step 3: create a media element with send_version_id being the media element
                    // version we just inserted. This media element will have no alternate versions
                    Long mediaElementId = sql.executeInsert([
                            version: 0,
                            send_version_id: sendVersionId,
                            uid: row.voicemail_key,
                            when_created: defaultNow
                        ], """
                        INSERT INTO media_element (
                            version,
                            send_version_id,
                            uid,
                            when_created)
                        VALUES (
                            :version,
                            :send_version_id,
                            :uid,
                            :when_created)
                    """)[0][0]
                    // step 4: associate the newly-created media element with the media info
                    // associated with this record call
                    sql.executeInsert([
                        mInfoId: row.media_id,
                        mElementId: mediaElementId
                    ], """
                        INSERT INTO media_info_media_element (
                            media_info_media_elements_id,
                            media_element_id)
                        VALUES (
                            :mInfoId,
                            :mElementId)
                    """)
                }
                // step 5: drop the voicemail_key column in the record_item table
                sql.execute("""
                    ALTER TABLE record_item
                    DROP COLUMN voicemail_key
                """)
            }
            rollback {
                // step 1: add a voicemail_key column to the record_item table
                sql.execute("""
                    ALTER TABLE record_item
                    ADD COLUMN voicemail_key VARCHAR(255) NULL
                """)
                // step 2: the below SQL query to restore the voicemail_key field is copied from
                // `migration-existing-call-cost-tracking.groovy`. We could try to restore from the
                // media_element but we could need to guess which media_element actually represents
                // the voicemail. Since we store the voicemails under the call's apiId and this
                // apiId information is stored in the receipts for billing tracking purposes, we
                // will use that source to restore the voicemail key
                sql.executeUpdate("""
                    UPDATE record_item AS i
                    INNER JOIN (
                            SELECT item_id, api_id, MAX(num_billable)
                            FROM record_item_receipt
                            WHERE status = "SUCCESS"
                            GROUP BY item_id
                            ORDER BY MAX(num_billable)
                        ) AS source
                        ON source.item_id = i.id
                    SET i.voicemail_key = source.api_id
                    WHERE i.class = "org.textup.RecordCall"
                        AND i.voicemail_in_seconds > 0
                        AND i.has_away_message = TRUE
                """)
                // Note that we will NOT delete the media_element that we copied the voicemail_key
                // back to have some redundancy in the data associating the voicemail with
                // this particular call.
            }
        }
    }
}
