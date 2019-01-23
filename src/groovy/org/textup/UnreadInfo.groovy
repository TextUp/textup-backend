package org.textup

import grails.compiler.GrailsTypeChecked
import org.joda.time.DateTime
import org.textup.util.domain.*

@GrailsTypeChecked
class UnreadInfo {

    final int numTexts
    final int numCalls
    final int numVoicemails

    static UnreadInfo create(Long recId, DateTime lastTouched) {
        Collection<RecordItem> rItems = RecordItems
            .forRecordIdsWithOptions([recId], lastTouched, null, [RecordText, RecordCall])
            .build(RecordItems.forIncoming())
            .list()
        new UnreadInfo(numText: forClass(rItems, RecordText).size(),
            numCalls: notVoicemail(forClass(rItems, RecordCall)).size(),
            numVoicemails: isVoicemail(forClass(rItems, RecordCall)).size())
    }

    // Helpers
    // -------

    protected <T extends RecordItem> Collection<T> forClass(Collection<? extends RecordItem> rItems,
        Class<T> clazz) {

        rItems.collect { RecordItem rItem1 -> clazz.isAssignableFrom(rItem1.class) }
    }

    protected Collection<RecordCall> notVoicemail(Collection<? extends RecordItem> rItems) {
        Collection<RecordCall> rCalls = []
        rItems.each { RecordItem rItem1 ->
            if (rItem1 instanceof RecordCall && !rItem1.isVoicemail) { rCalls << rItem1 }
        }
        rCalls
    }

    protected Collection<RecordCall> isVoicemail(Collection<? extends RecordItem> rItems) {
        Collection<RecordCall> rCalls = []
        rItems.each { RecordItem rItem1 ->
            if (rItem1 instanceof RecordCall && rItem1.isVoicemail) { rCalls << rItem1 }
        }
        rCalls
    }
}
