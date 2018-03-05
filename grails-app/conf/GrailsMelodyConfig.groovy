/*
You can find all detailed parameter usage from
https://github.com/javamelody/javamelody/wiki/UserGuide#6-optional-parameters
Any parameter with 'javamelody.' prefix configured in this file will be add as init-param of java melody MonitoringFilter.
 */

String customDir = System.getenv("JAVA_MELODY_STORAGE_DIRECTORY") ?: System.getProperty("JAVA_MELODY_STORAGE_DIRECTORY")
if (customDir) {
    javamelody.'storage-directory' = customDir
}

/*
Turn on Grails Service monitoring by adding 'spring' in displayed-counters parameter.
 */
javamelody.'displayed-counters' = 'http,sql,error,log,spring,jsp'
