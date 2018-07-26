package org.textup.util

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target
import org.textup.type.LogLevel

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface RollbackOnResultFailure {
    // declare this as `value` so that we don't have to pass in the key name when customizing
    // the log level when using this annotation. See https://stackoverflow.com/a/588065
    LogLevel value() default LogLevel.DEBUG
}
