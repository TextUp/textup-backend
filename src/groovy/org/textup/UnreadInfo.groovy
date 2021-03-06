package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import groovy.transform.TupleConstructor
import org.joda.time.DateTime
import org.textup.util.domain.*

@EqualsAndHashCode
@GrailsTypeChecked
@TupleConstructor(includeFields = true)
class UnreadInfo {

    final int numTexts
    final int numCalls
    final int numVoicemails

    static UnreadInfo create(Long recId, DateTime lastTouched) {
        Collection<RecordItem> rItems = RecordItems
            .buildForRecordIdsWithOptions([recId], lastTouched, null, [RecordText, RecordCall])
            .build(RecordItems.forIncoming())
            .list()
        new UnreadInfo(forClass(rItems, RecordText).size(),
            notVoicemail(forClass(rItems, RecordCall)).size(),
            isVoicemail(forClass(rItems, RecordCall)).size())
    }

    // Helpers
    // -------

    protected static <T extends RecordItem> Collection<T> forClass(Collection<? extends RecordItem> rItems,
        Class<T> clazz) {

        rItems.findAll { RecordItem rItem1 -> clazz.isAssignableFrom(rItem1.class) }
    }

    protected static Collection<RecordCall> notVoicemail(Collection<? extends RecordItem> rItems) {
        Collection<RecordCall> rCalls = []
        rItems.each { RecordItem rItem1 ->
            if (rItem1 instanceof RecordCall) {
                RecordCall rCall1 = rItem1 as RecordCall
                if (!rCall1.isVoicemail) { rCalls << rCall1 }
            }
        }
        rCalls
    }

    protected static Collection<RecordCall> isVoicemail(Collection<? extends RecordItem> rItems) {
        Collection<RecordCall> rCalls = []
        rItems.each { RecordItem rItem1 ->
            if (rItem1 instanceof RecordCall) {
                RecordCall rCall1 = rItem1 as RecordCall
                if (rCall1.isVoicemail) { rCalls << rCall1 }
            }
        }
        rCalls
    }
}
