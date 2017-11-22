grails.servlet.version = "3.0" // Change depending on target container compliance (2.5 or 3.0)
grails.project.class.dir = "target/classes"
grails.project.test.class.dir = "target/test-classes"
grails.project.test.reports.dir = "target/test-reports"
grails.project.work.dir = "target/work"
grails.project.target.level = 1.6
grails.project.source.level = 1.6
grails.project.war.file = "target/${appName}.war"

grails.project.fork = [
    // configure settings for compilation JVM, note that if you alter the Groovy version forked compilation is required
    //  compile: [maxMemory: 256, minMemory: 64, debug: false, maxPerm: 256, daemon:true],

    // configure settings for the test-app JVM, uses the daemon by default
    test: [maxMemory: 768, minMemory: 64, debug: false, maxPerm: 256, daemon:true],
    // configure settings for the run-app JVM
    run: [maxMemory: 768, minMemory: 64, debug: false, maxPerm: 256, forkReserve:false],
    // configure settings for the run-war JVM
    war: [maxMemory: 768, minMemory: 64, debug: false, maxPerm: 256, forkReserve:false],
    // configure settings for the Console UI JVM
    console: [maxMemory: 768, minMemory: 64, debug: false, maxPerm: 256]
]

grails.project.dependency.resolver = "maven" // or ivy
grails.project.dependency.resolution = {
    // inherit Grails' default dependencies
    inherits("global") {
        // specify dependency exclusions here; for example, uncomment this to disable ehcache:
        // excludes 'ehcache'
    }
    log "error" // log level of Ivy resolver, either 'error', 'warn', 'info', 'debug' or 'verbose'
    checksums true // Whether to verify checksums on resolve
    legacyResolve false // whether to do a secondary resolve on plugin installation, not advised and here for backwards compatibility

    repositories {
        inherits true // Whether to inherit repository definitions from plugins

        grailsPlugins()
        grailsHome()
        mavenLocal()
        grailsCentral()
        mavenCentral()
        // uncomment these (or add new ones) to enable remote dependency resolution from public Maven repositories
        //mavenRepo "http://repository.codehaus.org"
        //mavenRepo "http://download.java.net/maven/2/"
        //mavenRepo "http://repository.jboss.com/maven2/"

        //for springsecurity rest
        mavenRepo "http://repo.spring.io/milestone"
    }

    dependencies {
        // specify dependencies here under either 'build', 'compile', 'runtime', 'test' or 'provided' scopes e.g.
        runtime "mysql:mysql-connector-java:5.1.29"
        test "org.grails:grails-datastore-test-support:1.0.2-grails-2.4"
        //for persistance of joda time classes in Hibernate 4
        compile "org.jadira.usertype:usertype.core:3.2.0.GA"
        //for GrailsWebUtil dependency in custom renderer
        runtime "org.springframework:spring-test:4.0.7.RELEASE"
        //for printing human-reading timestamps
        compile "org.ocpsoft.prettytime:prettytime:3.0.2.Final"

        //for twilio plugin assisting with api calls
        compile "com.twilio.sdk:twilio:7.9.1"
        //for sending emails
        compile "com.sendgrid:sendgrid-java:4.1.2"
        //amazon cloudfront, all aws sdk dependencies must same version to avoid conflicts
        compile "com.amazonaws:aws-java-sdk-cloudfront:1.10.11"
        //amazon s3. newer version 1.10.32 returns "NoSuchFieldError: INSTANCE"
        compile "com.amazonaws:aws-java-sdk-s3:1.10.11"
        //pusher for realtime status updates and notifications
        compile "com.pusher:pusher-http-java:0.9.3"
        // reconcile http components versions manually
        compile "org.apache.httpcomponents:httpcore:4.4.4"
        build "org.apache.httpcomponents:httpcore:4.4.4"
        runtime "org.apache.httpcomponents:httpcore:4.4.4"
        // using 4.5.3 httpclient reveals a bug in the aws s3 sdk in which
        // operations somehow end up with
        // 'java.lang.IllegalStateException: Socket not created by this factory'
        compile "org.apache.httpcomponents:httpclient:4.5.2"
        build "org.apache.httpcomponents:httpclient:4.5.2"
        runtime "org.apache.httpcomponents:httpclient:4.5.2"

        // for spring security rest 1.5.3
        compile "xml-apis:xml-apis:1.4.01"
        // for encrypted jwts, exclude outdated dependencies to prevent conflict
        // see Token Storage section of spring-security-rest docs for 1.5.3
        // build("com.lowagie:itext:2.0.8") {
        //     excludes "bouncycastle:bcprov-jdk14:138", "org.bouncycastle:bcprov-jdk14:1.38"
        // }
    }

    plugins {
        // plugins for the build system only
        build ":tomcat:7.0.55"

        // plugins for the compile step
        compile "org.grails.plugins:codenarc:0.24.1" //static source code analysis
        compile ":scaffolding:2.1.2"
        compile ':cache:1.1.8'
        compile ":asset-pipeline:1.9.9"
        compile ":joda-time:1.5"
        compile "org.grails.plugins:quartz:1.0.2"

        compile ":spring-security-core:2.0-RC4"
        compile ":spring-security-rest:1.5.3", {
            excludes: 'spring-security-core'
        } //includes cors
        compile ":functional-spock:0.7"

        compile ":rest-client-builder:2.0.4-SNAPSHOT"
        compile ":remote-control:2.0"

        //for api documentation generation
        compile ":rest-api-doc:0.6.1"

        // plugins needed at runtime but not for compilation
        runtime ":hibernate4:4.3.6.1" // or ":hibernate:3.6.10.18"
        runtime ":database-migration:1.4.0"
        runtime ":console:1.5.4"
        runtime ":twitter-bootstrap:3.3.2.1"
        runtime ":jquery:1.11.1"
    }
}
