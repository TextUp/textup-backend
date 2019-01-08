package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode

// TODO remove this class

@GrailsTypeChecked
@EqualsAndHashCode
class SharedIndividualPhoneRecord extends PhoneRecord {

    IndividualPhoneRecord source

    static mapping = {
        source fetch: "join", column: "shared_individual_source", cascade: "save-update"
    }
    static constraints = {
        permission validator: { SharePermission val ->
            if (val == null) { ["mustSpecifySharingPermission"] }
        }
        source cascadeValidation: true, validator: { IndividualPhoneRecord val, SharedIndividualPhoneRecord obj ->
            if (val.phone.id == obj.phone.id) { ["shareWithMyself"] }
        }
    }

    // Methods
    // -------

    @Override
    IndividualPhoneRecordWrapper toWrapper() {
        new IndividualPhoneRecordWrapper(source, toPermissions(), this)
    }

    // Properties
    // ----------

    @Override
    String getSecureName() { source?.secureName }
}
