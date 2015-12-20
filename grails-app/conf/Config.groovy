// locations to search for config files that get merged into the main config;
// config files can be ConfigSlurper scripts, Java properties files, or classes
// in the classpath in ConfigSlurper format

// grails.config.locations = [ "classpath:${appName}-config.properties",
//                             "classpath:${appName}-config.groovy",
//                             "file:${userHome}/.grails/${appName}-config.properties",
//                             "file:${userHome}/.grails/${appName}-config.groovy"]

// if (System.properties["${appName}.config.location"]) {
//    grails.config.locations << "file:" + System.properties["${appName}.config.location"]
// }
grails.app.context = "/"
grails.project.groupId = appName // change this to alter the default package name and Maven publishing destination

// The ACCEPT header will not be used for content negotiation for user agents containing the following strings (defaults to the 4 major rendering engines)
grails.mime.disable.accept.header.userAgents = ['Gecko', 'WebKit', 'Presto', 'Trident']
grails.mime.types = [ // the first one is the default format
    all:           '*/*', // 'all' maps to '*' or the first available format in withFormat
    atom:          'application/atom+xml',
    css:           'text/css',
    csv:           'text/csv',
    form:          'application/x-www-form-urlencoded',
    html:          ['text/html','application/xhtml+xml'],
    js:            'text/javascript',
    json:          ['application/json', 'text/json'],
    multipartForm: 'multipart/form-data',
    rss:           'application/rss+xml',
    text:          'text/plain',
    hal:           ['application/hal+json','application/hal+xml'],
    xml:           ['text/xml', 'application/xml']
]

// URL Mapping Cache Max Size, defaults to 5000
//grails.urlmapping.cache.maxsize = 1000

// Legacy setting for codec used to encode data with ${}
grails.views.default.codec = "html"

// The default scope for controllers. May be prototype, session or singleton.
// If unspecified, controllers are prototype scoped.
grails.controllers.defaultScope = 'singleton'

// GSP settings
grails {
    views {
        gsp {
            encoding = 'UTF-8'
            htmlcodec = 'xml' // use xml escaping instead of HTML4 escaping
            codecs {
                expression = 'html' // escapes values inside ${}
                scriptlet = 'html' // escapes output from scriptlets in GSPs
                taglib = 'none' // escapes output from taglibs
                staticparts = 'none' // escapes output from static template parts
            }
        }
        // escapes all not-encoded output at final stage of outputting
        // filteringCodecForContentType.'text/html' = 'html'
    }
}


grails.converters.encoding = "UTF-8"
// scaffolding templates configuration
grails.scaffolding.templates.domainSuffix = 'Instance'

// Set to false to use the new Grails 1.2 JSONBuilder in the render method
grails.json.legacy.builder = false
// enabled native2ascii conversion of i18n properties files
grails.enable.native2ascii = true
// packages to include in Spring bean scanning
grails.spring.bean.packages = []
// whether to disable processing of multi part requests
grails.web.disable.multipart=false

// request parameters to mask when logging exceptions
grails.exceptionresolver.params.exclude = ['password']

// configure auto-caching of queries by default (if false you can cache individual queries with 'cache: true')
grails.hibernate.cache.queries = false

// configure passing transaction's read-only attribute to Hibernate session, queries and criterias
// set "singleSession = false" OSIV mode in hibernate configuration after enabling
grails.hibernate.pass.readonly = false
// configure passing read-only to OSIV session by default, requires "singleSession = false" OSIV mode
grails.hibernate.osiv.readonly = false

environments {
    development {
        grails.logging.jul.usebridge = true
        grails.plugin.databasemigration.updateOnStart = false
        textup.apiKeys.twilio.appId="AP762342f6263b687fdc60c12dc9fbded8"
    }
    production {
        grails.logging.jul.usebridge = false
        // TODO: grails.serverURL = "http://www.changeme.com"
        grails.plugin.databasemigration.updateOnStart = true
        grails.plugin.databasemigration.updateOnStartFileNames = ['changelog.groovy']
        textup.apiKeys.twilio.appId="AP7f44379a93c897199e9938fd5b6b3e60"
    }
}

// log4j configuration
log4j.main = {
    // Example of changing the log pattern for the default console appender:
    //
    //appenders {
    //    console name:'stdout', layout:pattern(conversionPattern: '%c{2} %m%n')
    //}

    error  'org.codehaus.groovy.grails.web.servlet',        // controllers
           'org.codehaus.groovy.grails.web.pages',          // GSP
           'org.codehaus.groovy.grails.web.sitemesh',       // layouts
           'org.codehaus.groovy.grails.web.mapping.filter', // URL mapping
           'org.codehaus.groovy.grails.web.mapping',        // URL mapping
           'org.codehaus.groovy.grails.commons',            // core / classloading
           'org.codehaus.groovy.grails.plugins',            // plugins
           'org.codehaus.groovy.grails.orm.hibernate',      // hibernate integration
           'org.springframework',
           'org.hibernate',
           'net.sf.ehcache.hibernate'
}


// Added by the Spring Security Core plugin:
grails.plugin.springsecurity.userLookup.userDomainClassName = 'org.textup.Staff'
grails.plugin.springsecurity.userLookup.authorityJoinClassName = 'org.textup.StaffRole'
grails.plugin.springsecurity.authority.className = 'org.textup.Role'
grails.plugin.springsecurity.controllerAnnotations.staticRules = [
    '/':                              ['permitAll'],
    '/index':                         ['permitAll'],
    '/index.gsp':                     ['permitAll'],
    '/assets/**':                     ['permitAll'],
    '/**/js/**':                      ['permitAll'],
    '/**/css/**':                     ['permitAll'],
    '/**/images/**':                  ['permitAll'],
    '/**/favicon.ico':                ['permitAll'],
    '/grails-remote-control':         ['permitAll'], //needed for remote control in functional tests
    '/dbconsole/**':                  ['ROLE_USER', 'ROLE_ADMIN'],
    "/console/**":                    ['ROLE_USER', 'ROLE_ADMIN'],
    "/plugins/console*/**":           ['ROLE_USER', 'ROLE_ADMIN'],
    '/restApiDoc':                    ['permitAll'],
    '/restApiDoc/**':                 ['permitAll'],
    '/reset':                         ['permitAll'],
    '/reset/**':                      ['permitAll'],
]
grails.plugin.springsecurity.filterChain.chainMap = [
    '/v1/public/**': 'anonymousAuthenticationFilter,restTokenValidationFilter,restExceptionTranslationFilter,filterInvocationInterceptor',
    '/v1/**': 'JOINED_FILTERS,-anonymousAuthenticationFilter,-exceptionTranslationFilter,-authenticationProcessingFilter,-securityContextPersistenceFilter,-rememberMeAuthenticationFilter',  // v1 rest api stateless
    '/api/**': 'JOINED_FILTERS,-anonymousAuthenticationFilter,-exceptionTranslationFilter,-authenticationProcessingFilter,-securityContextPersistenceFilter,-rememberMeAuthenticationFilter',  // api utility methods stateless
    '/**': 'JOINED_FILTERS,-restTokenValidationFilter,-restExceptionTranslationFilter'
]
grails {
    plugin {
        springsecurity {
            rest {
                login {
                    useJsonCredentials = true
                    usernamePropertyName = "username"
                    endpointUrl = "/login"
                }
                token {
                    rendering.usernamePropertyName = "username"
                    rendering.authoritiesPropertyName = "roles"
                    rendering.tokenPropertyName = "access_token"
                    validation.useBearerToken = true
                    validation.enableAnonymousAccess = true
                }
            }
        }
    }
}
cors.headers = ['Access-Control-Allow-Headers': 'Content-Type, Authorization']

//REST API documentation
grails.plugin.location.restapidoc = "PATH_TO_RESTAPIDOC"
grails.plugins.restapidoc.customClassName="org.textup.rest.CustomResponseDoc"
grails.plugins.restapidoc.outputFileGeneration="web-app/restapidoc.json"
grails.plugins.restapidoc.outputFileReading="restapidoc.json"

textup {
    maxNumText = 50 //max number of recipients to text
    defaultMax = 10 //default max during pagination
    largestMax = 100 //largest max allowed during pagination
    resetTokenSize = 25

    voicemailBucketName = "media.textup.org"
    mail {
        standard {
            name = "TextUp Notification"
            email = "no-reply@textup.org"
        }
        self {
            name = "TextUp"
            email = "connect@textup.org"
        }
    }
    links {
        passwordReset = "https://app.textup.org/#/reset?token="
        setupNewOrg = "https://app.textup.org/#/setup"
        setupExistingOrg = "https://app.textup.org/#/setup"
    }

    //On Tomcat7 on EC2, these are set in /etc/tomcat7/tomcat7.conf
    //in the format: JAVA_OPTS="${JAVA_OPTS} -Dkey=value"
    apiKeys {
        twilio {
            sid = System.getenv("TWILIO_SID") ?: System.getProperty("TWILIO_SID")
            authToken = System.getenv("TWILIO_AUTH") ?: System.getProperty("TWILIO_AUTH")
            unavailable="assigned"
            available="unassigned"
        }
        aws {
            accessKey = System.getenv("AWS_ACCESS_KEY") ?: System.getProperty("AWS_ACCESS_KEY")
            secretKey = System.getenv("AWS_SECRET_KEY") ?: System.getProperty("AWS_SECRET_KEY")
        }
        sendGrid {
            username = System.getenv("SENDGRID_USERNAME") ?: System.getProperty("SENDGRID_USERNAME")
            password = System.getenv("SENDGRID_PASSWORD") ?: System.getProperty("SENDGRID_PASSWORD")
            templateIds {
                standard = "6c024f79-e180-4f96-bfad-4178dc0204ab"
            }
        }
        pusher {
            appId = "159936"
            apiKey = System.getenv("PUSHER_API_KEY") ?: System.getProperty("PUSHER_API_KEY")
            apiSecret = System.getenv("PUSHER_API_SECRET") ?: System.getProperty("PUSHER_API_SECRET")
        }
    }
    rest {
        defaultLabel = "default" //default is to link to relationships
        detailLabel = "detail" //detailed view
        v1 {
            contact = [singular:"contact", plural:"contacts"]
            record = [singular:"record", plural:"records"]
            tag = [singular:"tag", plural:"tags"]
            staff = [singular:"staff", plural:"staff"]
            team = [singular:"team", plural:"teams"]
            organization = [singular:"organization", plural:"organizations"]
        }
    }
}
