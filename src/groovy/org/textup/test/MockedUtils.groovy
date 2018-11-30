package org.textup.test

import grails.compiler.GrailsTypeChecked
import groovy.transform.TypeCheckingMode
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ImportCustomizer
import org.codehaus.groovy.reflection.*
import org.codehaus.groovy.runtime.MethodClosure
import org.textup.*

@GrailsTypeChecked
class MockedUtils {

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static Object getMetaClassProperty(Object obj, String propName) {
        obj.metaClass.hasMetaProperty(propName)
            ? obj.metaClass.getMetaProperty(propName).initialValue
            : null
    }
    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static void setMetaClassProperty(Object obj, String propName, Object newVal) {
        obj.metaClass.setProperty(propName, newVal)
    }

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static void setMetaClassMethod(Object obj, String methodName, Object methodAction) {
        if (obj instanceof Class) {
            obj.metaClass["static"].setProperty(methodName, methodAction)
        }
        else { obj.metaClass.setProperty(methodName, methodAction) }
    }

    // we extract the original method with the maximum number of args
    static Closure extractOriginalMethod(List<MetaMethod> metaMethods, ClassLoader classLoader) {
        List<Tuple<CachedClass, Boolean>> info = normalizedArgumentTypes(metaMethods)
        List<String> allArgNames = ["delegate"]
        allArgNames.addAll(getArgNames(info.size()))

        MetaMethod methodWithMostArgs = metaMethods.max { MetaMethod m1 -> m1.parameterTypes.length }
        Binding variableBindings = new Binding(methodWithMostArgs: methodWithMostArgs)
        List<String> statements = ["methodWithMostArgs.invoke(${allArgNames.join(", ")})".toString()]
        buildClosure(info, classLoader, variableBindings, statements)
    }
    // when wrapping original, we pass all the args into the stored original method because, when
    // we were extracting the original method, we make sure to pull out the variant that took the
    // largest number of arguments
    static Closure buildOriginal(List<MetaMethod> metaMethods, ClassLoader classLoader,
        MethodClosure originalAction) {

        List<Tuple<CachedClass, Boolean>> info = normalizedArgumentTypes(metaMethods)
        List<String> allArgNames = getArgNames(info.size())

        Binding variableBindings = new Binding(originalAction: originalAction)
        List<String> statements = ["originalAction.doCall(${allArgNames.join(", ")})".toString()]
        buildClosure(info, classLoader, variableBindings, statements)
    }
    static Closure buildOverride(List<MetaMethod> metaMethods, ClassLoader classLoader,
        List<List<?>> callArgs, Closure action = null) {

        List<Tuple<CachedClass, Boolean>> info = normalizedArgumentTypes(metaMethods)
        List<String> allArgNames = getArgNames(info.size())

        Binding variableBindings = new Binding(action: action, callArgs: callArgs)
        List<String> statements = ["callArgs << [${allArgNames.join(', ')}]".toString()]
        if (action) {
            // need to do min of all args and closure max args because a closure without an
            // explicit `->` has an implicit argument `it` so will have max of 1 parameter even
            // if the method being overriden takes no parameters
            int overrideActionNumArgs = Math.min(allArgNames.size(), action.maximumNumberOfParameters ?: 0)
            List<String> overrideArgNames = overrideActionNumArgs
                ? allArgNames[0..(overrideActionNumArgs - 1)]
                : allArgNames
            statements << "action.call(${overrideArgNames.join(", ")})".toString()
        }
        else {
            Class returnType = normalizeReturnType(metaMethods)
            statements << "${getDefaultValue(returnType)}".toString()
        }
        buildClosure(info, classLoader, variableBindings, statements)
    }

    // Helpers
    // -------

    protected static Closure buildClosure(List<Tuple<CachedClass, Boolean>> info,
        ClassLoader classLoader, Binding variableBindings, List<String> statements) {
        // step 1: collect more info
        HashSet<String> packages = getPackages(info)
        List<String> argSignatures = getSignatures(info)
        // step 2: assemble closure
        String closureString = buildClosureString(argSignatures, statements)
        parseClosureString(classLoader, closureString, packages, variableBindings)
    }

    protected static HashSet<String> getPackages(List<Tuple<CachedClass, Boolean>> info) {
        HashSet<String> packages = new HashSet<>()
        info.each { Tuple<CachedClass, Boolean> processed ->
            Class clazz = processed.first.theClass
            // null check because primitive arg types will not have a package
            if (clazz.package) { packages << clazz.package.name }
        }
        packages
    }

    protected static List<String> getSignatures(List<Tuple<CachedClass, Boolean>> info) {
        List<String> argSignatures = []
        List<String> allArgNames = getArgNames(info.size())
        info.eachWithIndex { Tuple<CachedClass, Boolean> processed, int i ->
            Class clazz = processed.first.theClass
            String argType = clazz.canonicalName
            String argName = allArgNames[i]
            Boolean isOptional = processed.second
            if (isOptional) {
                argSignatures << "${argType} ${argName} = ${getDefaultValue(clazz)}".toString()
            }
            else { argSignatures << "${argType} ${argName}".toString() }
        }
        argSignatures
    }

    protected static List<String> getArgNames(int totalNumArgs) {
        List<String> allArgNames = []
        totalNumArgs.times { int i -> allArgNames << "a${i}".toString() }
        allArgNames
    }

    protected static String buildClosureString(List<String> argSignatures, List<String> statements) {
        "{ ${argSignatures.join(', ')} -> ${statements.join('; ')} }".toString()
    }

    protected static Closure parseClosureString(ClassLoader classLoader, String closureString,
        Collection<String> packagesToImport, Binding variableBindings) {

        ImportCustomizer customizer = new ImportCustomizer()
        packagesToImport.each { String pkg -> customizer.addStarImports(pkg) }
        CompilerConfiguration config = new CompilerConfiguration()
        config.addCompilationCustomizers(customizer)
        // need to pass in the current classloader so that textup classes are resolvable
        new GroovyShell(classLoader, variableBindings, config).evaluate(closureString) as Closure
    }

    // handles case of optional parameters. This method will throw an exception if this method is
    // overridden with varying method signatures such that we cannot override with a single closure.
    // If handling of simple optional parameters is enough, then this method returns a list of
    // tuples containin the class and a boolean indicating whether or not the class is optional (= null)
    protected static List<Tuple<CachedClass, Boolean>> normalizedArgumentTypes(List<MetaMethod> metaMethods) {
        // step 1: build a list of all of the parameter classes at each position
        List<List<CachedClass>> classesAtEachPosition = [].withDefault { [] }
        metaMethods.each { MetaMethod method ->
            method.parameterTypes.eachWithIndex { CachedClass clazz, int i ->
                classesAtEachPosition[i] << clazz
            }
        }
        // if method ot override has no arguments
        if (classesAtEachPosition.isEmpty()) { return [] }
        // step 2: step through each position in the parameter list. If completely different classes
        // at a certain position, then overriding via a single closure is not possible -- throw an
        // informative error in this case and return
        boolean hasNoArgSignature = metaMethods.any { MetaMethod m1 -> m1.parameterTypes.length == 0 }
        List<Tuple<CachedClass, Boolean>> normalizedArgs = []
        // first position is guaranteed to have the max num of args
        int maxNumOfArgVariations = classesAtEachPosition[0].size()
        classesAtEachPosition.each { List<CachedClass> clazzList ->
            if (clazzList.unique(false).size() > 1) {
                throw new UnsupportedOperationException("Method has multiple overloaded signatures and cannot be overridden with a single closure.")
            }
            boolean isOptional = (clazzList.size() < maxNumOfArgVariations || hasNoArgSignature)
            normalizedArgs << Tuple.create(clazzList[0], isOptional)
        }
        normalizedArgs
    }

    protected static Class normalizeReturnType(List<MetaMethod> metaMethods) {
        List<Class> returnTypes = []
        metaMethods.each { MetaMethod method -> returnTypes << method.returnType }
        if (returnTypes.unique(false).size() > 1) {
            throw new UnsupportedOperationException("Method has multiple return types and cannot be overridden with a single closure.")
        }
        returnTypes[0]
    }

    // See https://stackoverflow.com/a/38729449 for a list of default values from the Java spec
    protected static <T> T getDefaultValue(Class<T> type) {
        switch(type) {
            case byte: return (byte)0
            case short: return (short)0
            case int: return 0
            case long: return 0L
            case float: return 0.0f
            case double: return 0.0d
            case char: return '\u0000'
            case boolean: return false
            default: return null
        }
    }
}
