package org.textup.util

import grails.compiler.GrailsTypeChecked
import groovy.transform.TypeCheckingMode
import org.textup.*
import org.codehaus.groovy.reflection.*

@GrailsTypeChecked
class MockedMethod {

    private List<List<?>> callArgs = []

    private final Object obj
    private final String methodName
    private final Closure action

    MockedMethod(Object thisObj, String thisMethodName, Closure thisAction = null) {
        obj = thisObj
        methodName = thisMethodName
        action = thisAction
        List<MetaMethod> metaMethods = getMetaMethods()
        if (!metaMethods) {
            throw new IllegalArgumentException("Cannot mock `${methodName}` on object of class `${obj?.class}`")
        }
        if (isAlreadyOverridden()) {
            throw new IllegalArgumentException("Method `${methodName}` on object of class `${obj?.class}` has already been overridden.")
        }
        startOverride(metaMethods)
    }

    int getCallCount() {
        callArgs.size()
    }

    List<Object[]> getCallArguments() {
        Collections.unmodifiableList(callArgs)
    }

    MockedMethod reset() {
        callArgs.clear()
        this
    }

    MockedMethod restore() {
        stopOverride()
        reset()
    }

    protected void startOverride(List<MetaMethod> metaMethods) {
        updateOriginalMethod(getTargetMethod())
        setTargetMethod(MockedUtils
            .buildOverride(metaMethods, this.class.classLoader, callArgs, action))
    }

    protected void stopOverride() {
        ExpandoMetaClass.ExpandoMetaProperty originalMethod = tryGetOriginalMethod()
        if (originalMethod) {
            setTargetMethod(originalMethod)
            // reset the original method key after restoring so that when if we mock again in the
            // future, we do not throw an "already mocked" exception
            updateOriginalMethod(null)
        }
    }

    // Status
    // ------

    protected List<MetaMethod> getMetaMethods() {
        obj.metaClass.respondsTo(obj, methodName)
    }

    protected boolean isAlreadyOverridden() {
        tryGetOriginalMethod() != null
    }

    // Property access
    // ---------------

    protected String getOriginalMethodKey() {
        methodName ? "_original-${methodName}" : null
    }

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    protected ExpandoMetaClass.ExpandoMetaProperty tryGetOriginalMethod() {
        obj.metaClass.hasMetaProperty(originalMethodKey)
            ? obj.metaClass.getMetaProperty(originalMethodKey).initialValue
            : null
    }

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    protected void updateOriginalMethod(Object originalMethod) {
        obj.metaClass.setProperty(originalMethodKey, originalMethod)
    }

    // Helper methods
    // --------------

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    protected ExpandoMetaClass.ExpandoMetaProperty getTargetMethod() {
        obj instanceof Class ? obj.metaClass["static"][methodName] : obj.metaClass[methodName]
    }

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    protected void setTargetMethod(Object overriden) {
        if (obj instanceof Class) {
            obj.metaClass["static"].setProperty(methodName, overriden)
        }
        else { obj.metaClass.setProperty(methodName, overriden) }
    }
}
