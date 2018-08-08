import groovy.sql.*
import org.textup.*

databaseChangeLog = {

    changeSet(author: "ericbai (generated)", id: "1533676560477-6") {
        addColumn(tableName: "future_message") {
            column(name: "media_id", type: "bigint")
        }
    }

    changeSet(author: "ericbai (generated)", id: "1533676560477-7") {
        addColumn(tableName: "record_item") {
            column(name: "media_id", type: "bigint")
        }
    }

    changeSet(author: "ericbai (generated)", id: "1533676560477-9") {
        addColumn(tableName: "record_note_revision") {
            column(name: "media_id", type: "bigint")
        }
    }

    changeSet(author: "ericbai (generated)", id: "1533676560477-21") {
        createIndex(indexName: "FK_8duohh4tg9984b0r76wjlvwi5", tableName: "future_message") {
            column(name: "media_id")
        }
    }

    changeSet(author: "ericbai (generated)", id: "1533676560477-27") {
        createIndex(indexName: "FK_3yme02b13sxcjph28ex5pm64i", tableName: "record_item") {
            column(name: "media_id")
        }
    }

    changeSet(author: "ericbai (generated)", id: "1533676560477-28") {
        createIndex(indexName: "FK_e9f4n5rnnsf70t8b6w3wx13js", tableName: "record_note_revision") {
            column(name: "media_id")
        }
    }

    changeSet(author: "ericbai (generated)", id: "1533676560477-13") {
        addForeignKeyConstraint(baseColumnNames: "media_id", baseTableName: "future_message", constraintName: "FK_8duohh4tg9984b0r76wjlvwi5", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "media_info", referencesUniqueColumn: "false")
    }

    changeSet(author: "ericbai (generated)", id: "1533676560477-19") {
        addForeignKeyConstraint(baseColumnNames: "media_id", baseTableName: "record_item", constraintName: "FK_3yme02b13sxcjph28ex5pm64i", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "media_info", referencesUniqueColumn: "false")
    }

    changeSet(author: "ericbai (generated)", id: "1533676560477-20") {
        addForeignKeyConstraint(baseColumnNames: "media_id", baseTableName: "record_note_revision", constraintName: "FK_e9f4n5rnnsf70t8b6w3wx13js", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "media_info", referencesUniqueColumn: "false")
    }

    changeSet(author: "ericbai", id: "1533676560477-30") {
        grailsChange {
            change { doDropImageKeys(sql, "record_item") }
            rollback { rollbackDropImageKeys(sql, "record_item") }
        }
    }

    changeSet(author: "ericbai", id: "1533676560477-32") {
        grailsChange {
            change { doDropImageKeys(sql, "record_note_revision") }
            rollback { rollbackDropImageKeys(sql, "record_note_revision") }
        }
    }
}

// Need to call toString() on GStrings. Special side-effects of GString when used in a SQL context!
// See: http://programmingitch.blogspot.com/2010/10/be-careful-using-gstrings-for-sql.html
def doDropImageKeys(Sql sql, String tableName) {
    // step 1: transfer all existing images to the new schema
    List<GroovyRowResult> existingImages = sql.rows("""
        SELECT id, image_keys_as_string FROM ${tableName}
        WHERE image_keys_as_string IS NOT NULL;
    """.toString())
    existingImages.each { GroovyRowResult row ->
        Long mInfoId = copyImageKeysToMedia(sql, row.id, row.image_keys_as_string)
        // step 2: associate media info with owner
        sql.executeUpdate([mInfoId: mInfoId, itemId: row.id], """
            UPDATE ${tableName}
            SET media_id = :mInfoId
            WHERE id = :itemId;
        """.toString())
    }
    // step 3: drop the image keys column
    sql.execute("""
        ALTER TABLE ${tableName}
        DROP COLUMN image_keys_as_string;
    """.toString())
}

// Need to call toString() on GStrings. Special side-effects of GString when used in a SQL context!
// See: http://programmingitch.blogspot.com/2010/10/be-careful-using-gstrings-for-sql.html
def rollbackDropImageKeys(Sql sql, String tableName) {
    sql.execute([table: tableName], """
        ALTER TABLE ${tableName}
        ADD COLUMN image_keys_as_string VARCHAR(255) NULL;
    """.toString())
}

protected Long copyImageKeysToMedia(Sql sql, Long id, String serializedData) {
    Object dataObj = Helpers.toJson(serializedData)
    if (!id || !(dataObj instanceof List)) {
        return
    }
    // step 1: create a media info obj
    Long mInfoId = sql.executeInsert([version: 0], """
        INSERT INTO media_info (version)
        VALUES (:version);
    """)[0][0]
    // step 2: add each image key as a media element
    dataObj.each { String imageKey ->
        // add the single verision as the send versino within the media element
        Long sendVersionId = sql.executeInsert([
            version: 0,
            key: imageKey,
            mVersion: "SEND",
            size: 100 // width is optional but size is mandatory so we put a made-up value here
        ], """
            INSERT INTO media_element_version (version, `key`, media_version, size_in_bytes)
            VALUES (:version, :key, :mVersion, :size);
        """)[0][0]
        // create the media element
        Long mElementId = sql.executeInsert([
            version: 0,
            sendId: sendVersionId,
            type: "IMAGE",
            uid: imageKey
        ], """
            INSERT INTO media_element (version, send_version_id, type, uid)
            VALUES (:version, :sendId, :type, :uid);
        """)[0][0]
        // associated created media element with media info parent
        sql.executeInsert([
            mInfoId: mInfoId,
            mElementId: mElementId
        ], """
            INSERT INTO media_info_media_element (media_info_media_elements_id, media_element_id)
            VALUES (:mInfoId, :mElementId);
        """)
    }
    mInfoId
}
