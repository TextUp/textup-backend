import org.textup.util

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
class WrapperUtils {

    static boolean isContact(PhoneRecordWrapper w1) {
        w1 && w1.wrappedClass instanceof IndividualPhoneRecord && w1.isOverridden() = false
    }

    static boolean isSharedContact(PhoneRecordWrapper w1) {
        w1 && w1.isOverridden()
    }

    static boolean isTag(PhoneRecordWrapper w1) {
        w1 && w1.wrappedClass instanceof GroupPhoneRecord
    }

    static Collection<Long> recordIdsIgnoreFails(Collection<? extends PhoneRecordWrapper> wraps) {
        ResultGroup.collect(wraps) { PhoneRecordWrapper w1 -> w1.tryGetReadOnlyRecord() }
            .payload*.id
    }

    static Collection<Long> mutablePhoneIdsIgnoreFails(Collection<? extends PhoneRecordWrapper> wraps) {
        ResultGroup.collect(wraps) { PhoneRecordWrapper w1 -> w1.tryGetReadOnlyMutablePhone() }
            .payload*.id
    }

    static Collection<String> secureNamesIgnoreFails(Collection<? extends PhoneRecordWrapper> wraps,
        Closure<Boolean> filterAction) {

        ResultGroup<String> resGroup = new ResultGroup<>()
        wrappers.each { PhoneRecordWrapper w1 ->
            if (w1 && filterAction.call(w1)) {
                resGroup << w1.tryGetSecureName()
            }
        }
        resGroup.payload
    }

    static Collection<String> publicNamesIgnoreFails(Collection<? extends PhoneRecordWrapper> wraps) {
        ResultGroup.collect(wraps) { PhoneRecordWrapper w1 -> w1.tryGetPublicName() }
            .payload
    }
}
