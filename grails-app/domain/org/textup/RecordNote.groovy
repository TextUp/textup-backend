package org.textup

import groovy.transform.EqualsAndHashCode
import org.restapidoc.annotation.*

@EqualsAndHashCode(callSuper=true)
@RestApiObject(name="RecordNote", description="A note entry in a contact's record.")
class RecordNote extends RecordItem {

    @RestApiObjectField(description = "Contents of the note")
	String note
    //for some system events, we add a note that isn't editable
    @RestApiObjectField(
        description    = "Specifies whether or not this note is editable",
        useForCreation = false)
    boolean editable = true

    @RestApiObjectFields(params=[
        @RestApiObjectField(
            apiFieldName      = "addToContact",
            description       = "Id of a contact to add this note to",
            allowedType       = "Number",
            mandatory         = true,
            useForCreation    = true,
            presentInResponse = false)
    ])
    static transients = []
    static constraints = {
    	note blank:false, size:1..250
    }
    static namedQueries = {
        forRecord { Record rec ->
            eq("record", rec)
            order("dateCreated", "desc")
        }
    }

    /*
	Has many:
	*/

    ////////////////////
    // Helper methods //
    ////////////////////

    /////////////////////
    // Property Access //
    /////////////////////

    void setNote(String n) {
        if (editable) {
            this.note = n
        }
        else if (!editable && !this.note) {
            this.note = n
        }
    }
}
