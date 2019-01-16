package org.textup

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
class UnreadInfo {

    final int numTexts
    final int numCalls
    final int numVoicemails

    UnreadInfo(Long recId, DateTime lastTouched) {
        Collection<RecordItem> rItems = tRecordItems
            .forRecordIdsWithOptions([recId], lastTouched, null, [RecordText, RecordCall])
            .build(RecordItems.forIncoming())
            .list()
        numTexts = forClass(rItems, RecordText).size()
        numCalls = notVoicemail(forClass(rItems, RecordCall)).size()
        numVoicemails = isVoicemail(forClass(rItems, RecordCall)).size()
    }

    // Helpers
    // -------

    protected <T extends RecordItem> Collection<T> forClass(Collection<? extends RecordItem> rItems,
        Clazz<T> clazz) {

        rItems.collect { RecordItem rItem1 -> rItem1 instanceof clazz }
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
