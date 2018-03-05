/*
You can find all detailed parameter usage from
https://github.com/javamelody/javamelody/wiki/UserGuide#6-optional-parameters
Any parameter with 'javamelody.' prefix configured in this file will be add as init-param of java melody MonitoringFilter.
 */

/*
Turn on Grails Service monitoring by adding 'spring' in displayed-counters parameter.
 */
javamelody.'displayed-counters' = 'http,sql,error,log,spring,jsp'

/*
Specify custom storage directory for files to persist between server restarts
 */
environments {
    production {
        javamelody.'storage-directory' = System.getenv("JAVA_MELODY_STORAGE_DIRECTORY") ?: System.getProperty("JAVA_MELODY_STORAGE_DIRECTORY")
    }
}
