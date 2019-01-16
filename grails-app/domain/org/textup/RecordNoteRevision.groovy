package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.restapidoc.annotation.*
import org.textup.type.AuthorType
import org.textup.validator.Author

@EqualsAndHashCode
class RecordNoteRevision implements ReadOnlyRecordNoteRevision, WithId, Saveable<RecordNoteRevision> {

    AuthorType authorType
    DateTime whenChanged
    Location location
    Long authorId
    String authorName
    MediaInfo media
    String noteContents

	static belongsTo = [note: RecordNote]
    static mapping = {
        whenChanged type: PersistentDateTime
        noteContents type: "text"
        location lazy: false, cascade: "save-update"
        media lazy: false, cascade: "save-update"
    }
    static constraints = {
    	importFrom RecordItem
    	importFrom RecordNote
    }

    @GrailsTypeChecked
    @Override
    ReadOnlyLocation getReadOnlyLocation() { location }

    @GrailsTypeChecked
    @Override
    ReadOnlyMediaInfo getReadOnlyMedia() { media }
}
