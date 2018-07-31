package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

@Domain([Record, RecordItem, RecordText, RecordCall, RecordItemReceipt,
    MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
@Unroll
class RecordTextSpec extends Specification {

    void "test contents length constraint"() {
    	when: "we have a record text"
    	Record rec = new Record()
    	RecordText rText = new RecordText(record:rec, contents:"hi")

    	then:
    	rText.validate() == true

    	when: "we add a too-short contents"
    	rText.contents = ""

    	then: "we don't have to contents because the message may just contain media \
            OutgoingMessage validator enforces this"
    	rText.validate() == true

    	when: "we add contents longer than two text message lengths"
    	rText.contents = '''
			Far far away, behind the word mountains, far from the countries Vokalia and
			Consonantia, there live the blind texts. Separated they live in Bookmarksgrove
			right at the coast of the Semantics, a large language ocean. A small river
			named Duden flows by their place and supplies it with the necessary regelialia.
			It is a paradisemati
		'''

    	then: "valid, removed length constraint to avoid rejecting incoming msgs"
    	rText.validate() == true

        when: "we add contents longer than supported by text column type"
        rText.noteContents = buildVeryLongString()

        then: "shared contraint on the noteContents field is executed"
        rText.validate() == false
        rText.errors.getFieldErrorCount("noteContents") == 1
    }

    // Helpers
    // -------

    protected String buildVeryLongString() {
        StringBuilder sBuilder = new StringBuilder()
        15000.times { it -> sBuilder << it }
        sBuilder.toString()
    }
}
