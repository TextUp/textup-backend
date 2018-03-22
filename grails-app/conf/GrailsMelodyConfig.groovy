/*
You can find all detailed parameter usage from
https://github.com/javamelody/javamelody/wiki/UserGuide#6-optional-parameters
Any parameter with 'javamelody.' prefix configured in this file will be add as init-param of java melody MonitoringFilter.

DO NOT use environment variables or properties here! When the WAR file is packaged, the
storage directory is hard-coded into the configuration in the WAR file.
 */

javamelody.'storage-directory' = '/grails-monitoring'

/*
Turn on Grails Service monitoring by adding 'spring' in displayed-counters parameter.
 */
javamelody.'displayed-counters' = 'http,sql,error,log,spring,jsp'

javamelody.'system-actions-enabled' = 'false'
