package org.textup.util

import grails.compiler.GrailsTypeChecked
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ImportCustomizer
import org.codehaus.groovy.reflection.*
import org.textup.*

@GrailsTypeChecked
class MockedUtils {

    static Closure buildOverride(List<MetaMethod> metaMethods, ClassLoader classLoader,
        List<List<?>> callArgs, Closure action) {
        int overrideActionNumArgs = action?.maximumNumberOfParameters ?: 0
        HashSet<String> packages = new HashSet<>()
        List<String> argSignatures = []
        List<String> allArgNames = []
        List<String> argNamestoPassToAction = []
        List<String> statements= []
        // step 1: build closure argument signatures
        normalizedArgumentTypes(metaMethods)
            .eachWithIndex { Tuple<CachedClass, Boolean> processed, int i ->
                CachedClass cachedClazz = processed.first
                Boolean isOptional = processed.second
                Class clazz = cachedClazz.theClass
                String argType = cachedClazz.name
                String argName = "a${i}"
                String signature = isOptional
                    ? "${argType} ${argName} = ${getDefaultValue(clazz)}"
                    : "${argType} ${argName}"
                // closure override has to exactly match the signature
                argSignatures << signature
                allArgNames << argName
                if (i < overrideActionNumArgs) {
                    argNamestoPassToAction << argName
                }
                // null check because primitive arg types will not have a package
                if (clazz.package) { packages << clazz.package.name }
            }
        // step 2: build statements that make up closure body
        Class returnType = normalizeReturnType(metaMethods)
        Binding ctx = new Binding(action: action, callArgs: callArgs)
        statements << "callArgs << [${allArgNames.join(', ')}];".toString()
        if (action) {
            statements << "action.call(${argNamestoPassToAction.join(", ")});".toString()
        }
        else { statements << "${getDefaultValue(returnType)};".toString() }
        // step 3: assemble closure string
        String closureString = "{ ${argSignatures.join(', ')} -> ${statements.join(' ')} }"
        // step 4: evaluate string with appropriate imports
        ImportCustomizer customizer = new ImportCustomizer()
        packages.each { String pkg -> customizer.addStarImports(pkg) }
        CompilerConfiguration config = new CompilerConfiguration()
        config.addCompilationCustomizers(customizer)
        // need to pass in the current classloader so that textup classes are resolvable
        new GroovyShell(classLoader, ctx, config).evaluate(closureString) as Closure
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
