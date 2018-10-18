package org.textup.util

import grails.compiler.GrailsTypeChecked
import org.codehaus.groovy.reflection.*
import org.codehaus.groovy.runtime.MethodClosure
import org.textup.*

// [NOTE] this means that default values stored on the original method declarations will be lost.
//      This is an existing limitation of this implementation.

@GrailsTypeChecked
class MockedMethod {

    private List<List<?>> callArgs = []

    private final Object obj
    private final String methodName
    private final Closure action

    MockedMethod(Object thisObj, String thisMethodName, Closure thisAction = null,
        boolean overrideIfExistingMock = false) {

        obj = thisObj
        methodName = thisMethodName
        action = thisAction
        List<MetaMethod> metaMethods = getMetaMethods()
        if (!metaMethods) {
            throw new IllegalArgumentException("Cannot mock `${methodName}` on object of class `${obj?.class}`")
        }
        if (overrideIfExistingMock == false && isAlreadyOverridden()) {
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

    // Only store the original method once. We store the original method as another "expando" method
    // on because storing closures as a metaproperty seems to make them unretrievable. Also, we no
    // longer just store a reference to the original ExpandoMetaProperty because isn't actually
    // the original method body, but rather a pointer to the method closure that we can override.
    // It does not seem to be possible to determine the presence of a method on an object without
    // actually calling it. Therefore, we also set primitives as expando metaproperties because
    // these seem to be retrievable and allow us to track (1) whether or not this particular method
    // is currently being mocked, and (2) whether or not we've already extracted and stored the
    // original method as an expando metamethod
    protected void startOverride(List<MetaMethod> metaMethods) {
        ClassLoader classLoader = this.class.classLoader
        Boolean hasOriginalMethod = MockedUtils.getMetaClassProperty(obj, originalMethodStatusKey)
        if (!hasOriginalMethod) {
            Closure originalMethod = MockedUtils.extractOriginalMethod(metaMethods, classLoader)
            MockedUtils.setMetaClassMethod(obj, originalMethodKey, originalMethod)
            MockedUtils.setMetaClassProperty(obj, originalMethodStatusKey, true)
        }
        Closure overridingAction = MockedUtils.buildOverride(metaMethods, classLoader, callArgs, action)
        MockedUtils.setMetaClassMethod(obj, methodName, overridingAction)
        MockedUtils.setMetaClassProperty(obj, currentStatusKey, true)
    }

    // Recall that we can't actually extract closures/methods that we store as expando metamethods
    // or expando metaproperties. Therefore, we need to obtain a reference to the dynamically-added
    // original method on the object itself and wrap this original method in a new closure
    // that we then set on the original method name to effectively restore the original behavior.
    // When we extract the original method, we always will pull the method variant that takes the
    // largest number of inputs so when we are wrapping this original method, we will automatically
    // pass in all possible arguments to the original method.
    protected void stopOverride() {
        // no need to stop override if we are not already overriding
        if (!isAlreadyOverridden()) { return }

        List<MetaMethod> metaMethods = getMetaMethods()
        ClassLoader classLoader = this.class.classLoader
        MethodClosure originalAction = obj.&"${originalMethodKey}" as MethodClosure
        Closure wrappedOriginalAction = MockedUtils.buildOriginal(metaMethods, classLoader, originalAction)

        MockedUtils.setMetaClassMethod(obj, methodName, wrappedOriginalAction)
        MockedUtils.setMetaClassProperty(obj, currentStatusKey, false)
    }

    // Status
    // ------

    protected List<MetaMethod> getMetaMethods() {
        obj.metaClass.respondsTo(obj, methodName)
    }

    protected boolean isAlreadyOverridden() {
        MockedUtils.getMetaClassProperty(obj, currentStatusKey)
    }

    // Property access
    // ---------------

    protected String getCurrentStatusKey() {
        methodName ? "currentMockStatus${methodName}" : ""
    }

    protected String getOriginalMethodStatusKey() {
        methodName ? "originalMethodStatus${methodName}" : ""
    }

    protected String getOriginalMethodKey() {
        methodName ? "original${methodName}" : ""
    }
}
