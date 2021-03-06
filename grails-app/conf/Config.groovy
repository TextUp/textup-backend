import org.codehaus.groovy.grails.validation.ConstrainedProperty
import org.textup.*
import org.textup.constraint.*
import org.textup.rest.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

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
    all           : '*/*', // 'all' maps to '*' or the first available format in withFormat
    atom          : 'application/atom+xml',
    css           : 'text/css',
    csv           : 'text/csv',
    form          : 'application/x-www-form-urlencoded',
    hal           : ['application/hal+json','application/hal+xml'],
    html          : ['text/html','application/xhtml+xml'],
    js            : 'text/javascript',
    json          : ['application/json', 'text/json'],
    multipartForm : 'multipart/form-data',
    pdf           : 'application/pdf',
    rss           : 'application/rss+xml',
    text          : 'text/plain',
    xml           : ['text/xml', 'application/xml'],
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
                expression  = 'html' // escapes values inside ${}
                scriptlet   = 'html' // escapes output from scriptlets in GSPs
                taglib      = 'none' // escapes output from taglibs
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
grails.exceptionresolver.params.exclude = ["password", "authToken", "lockCode"]

// configure auto-caching of queries by default (if false you can cache individual queries with 'cache: true')
grails.hibernate.cache.queries = false
// configure passing transaction's read-only attribute to Hibernate session, queries and criterias
// set "singleSession = false" OSIV mode in hibernate configuration after enabling
grails.hibernate.pass.readonly = false
// configure passing read-only to OSIV session by default, requires "singleSession = false" OSIV mode
grails.hibernate.osiv.readonly = false

environments {
    development {
        grails.logging.jul.usebridge                  = true
        grails.plugin.databasemigration.updateOnStart = false
        grails.plugin.console.baseUrl                 = "http://localhost:8080/console"
        textup.export.imagePath                       = "web-app/images/logo.png"
    }
    test {
        textup.export.imagePath = "web-app/images/logo.png"
    }
    production {
        grails.logging.jul.usebridge                           = false
        grails.serverURL                                       = System.getenv("TEXTUP_BACKEND_SERVER_URL") ?: System.getProperty("TEXTUP_BACKEND_SERVER_URL")
        grails.plugin.databasemigration.updateOnStart          = true
        grails.plugin.databasemigration.updateOnStartFileNames = ['changelog.groovy']
        // ignore all of the Quartz scheduler tables created via direct SQL execution
        grails.plugin.databasemigration.ignoredObjects = [
            'QRTZ_BLOB_TRIGGERS',
            'QRTZ_CALENDARS',
            'QRTZ_CRON_TRIGGERS',
            'QRTZ_FIRED_TRIGGERS',
            'QRTZ_JOB_DETAILS',
            'QRTZ_LOCKS',
            'QRTZ_PAUSED_TRIGGER_GRPS',
            'QRTZ_SCHEDULER_STATE',
            'QRTZ_SIMPLE_TRIGGERS',
            'QRTZ_SIMPROP_TRIGGERS',
            'QRTZ_TRIGGERS'
        ]
        // on Tomcat7, FopFactory's context root is `/var/lib/tomcat7/./`
        textup.export.imagePath = "webapps/ROOT/images/logo.png"
    }
}

// log4j configuration
log4j.main = {
    // Example of changing the log pattern for the default console appender:
    //
    //appenders {
    //    console name:'stdout', layout:pattern(conversionPattern: '%c{2} %m%n')
    //}

    // // For debugging access-restricted page issues
    // // see http://alvarosanchez.github.io/grails-spring-security-rest/1.5.3/docs/guide/debugging.html
    // debug  'grails.plugin.springsecurity',
    //        'grails.app.controllers.grails.plugin.springsecurity',
    //        'grails.app.services.grails.plugin.springsecurity',
    //        'org.pac4j',
    //        'org.springframework.security'

    // // For printing out the data values bound to the printed Hibernate SQL statements
    // // see https://therealdanvega.com/blog/2013/08/20/grails-hibernate-logging
    // trace  'org.hibernate.type.descriptor.sql.BasicBinder'
    // // For printing Hibernate SQL statements
    // debug  'org.hibernate.SQL'
    // // For greater visibility into caching configuration + setup
    // debug  'grails.plugin.cache'

    // For greater visibility into jobs
    info   'grails.app.jobs'                                // Quartz jobs

    // Error loggers
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
           'net.sf.ehcache.hibernate',
           'net.bull.javamelody',                            // Melody monitoring
           'grails.plugin.cache'
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
    '/grails-remote-control':         ['permitAll'], // needed for remote control in functional tests
    '/monitoring/**':                 ['ROLE_USER', 'ROLE_ADMIN'],
    '/dbconsole/**':                  ['ROLE_USER', 'ROLE_ADMIN'],
    "/console/**":                    ['ROLE_USER', 'ROLE_ADMIN'],
    "/plugins/console*/**":           ['ROLE_USER', 'ROLE_ADMIN'],
]
grails.plugin.springsecurity.filterChain.chainMap = [
    '/v1/public/**': 'anonymousAuthenticationFilter,restTokenValidationFilter,restExceptionTranslationFilter,filterInvocationInterceptor',
    '/v1/**': 'JOINED_FILTERS,-anonymousAuthenticationFilter,-exceptionTranslationFilter,-authenticationProcessingFilter,-securityContextPersistenceFilter,-rememberMeAuthenticationFilter',  // v1 rest api stateless
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

// NOTE: v3.0.0 docs are NOT THE SAME as v1.1.8 docs. For example, 1.1.8 does NOT have
// `GrailsConcurrentLinkedMapCacheManager` so we have to override the grailsCacheManager bean manually
// For 1.1.8 docs: https://github.com/grails-plugins/grails-cache/tree/v1.1.8/src/docs
// To configure max size of certain caches, go to `conf/spring/resources.groovy` and
// add to the passed-in `cacheNameToMaxSize` map
grails.cache.enabled = true
grails.cache.clearAtStartup = true
grails.cache.config = {
   cache { name Constants.CACHE_RECEIPTS }
   cache { name Constants.CACHE_PHONES }
}

// this property seems to only set CORS headers in response to the preflight request
cors.headers = ["Access-Control-Allow-Headers": "Content-Type, Authorization"]
// this property sets Access-Control-Expose-Headers
// see: https://developer.mozilla.org/en-US/docs/Web/HTTP/CORS#Access-Control-Expose-Headers
cors.expose.headers = "Content-Disposition"

//General developer documentation
grails.doc.title    = "TextUp"
grails.doc.subtitle = "A getting started guide to the backend and frontend codebases that make up the TextUp application"
grails.doc.authors  = "Eric Bai"
grails.doc.license  = "Apache 2.0"

// Custom constraints
ConstrainedProperty.registerNewConstraint(CascadeValidationConstraint.NAME, CascadeValidationConstraint.class)
ConstrainedProperty.registerNewConstraint(PhoneNumberConstraint.NAME, PhoneNumberConstraint.class)

textup {
    tempDirectory = System.getenv("TEXTUP_BACKEND_TEMP_DIRECTORY") ?: System.getProperty("TEXTUP_BACKEND_TEMP_DIRECTORY")
    media {
        bucketName = System.getenv("TEXTUP_BACKEND_STORAGE_BUCKET_NAME") ?: System.getProperty("TEXTUP_BACKEND_STORAGE_BUCKET_NAME")
        cdn {
            root           = System.getenv("TEXTUP_BACKEND_CDN_ROOT") ?: System.getProperty("TEXTUP_BACKEND_CDN_ROOT")
            keyId          = System.getenv("TEXTUP_BACKEND_CDN_KEY_ID") ?: System.getProperty("TEXTUP_BACKEND_CDN_KEY_ID")
            privateKeyPath = System.getenv("TEXTUP_BACKEND_CDN_PRIVATE_KEY_PATH") ?: System.getProperty("TEXTUP_BACKEND_CDN_PRIVATE_KEY_PATH")
        }
        audio {
            executableDirectory = System.getenv("TEXTUP_BACKEND_FFMPEG_DIRECTORY") ?: System.getProperty("TEXTUP_BACKEND_FFMPEG_DIRECTORY")
            executableName      = System.getenv("TEXTUP_BACKEND_FFMPEG_COMMAND") ?: System.getProperty("TEXTUP_BACKEND_FFMPEG_COMMAND")
        }
    }
    mail {
        standard {
            name  = "TextUp Notification"
            email = "no-reply@textup.org"
        }
        self {
            name  = "TextUp"
            email = "connect@textup.org"
        }
    }
    links {
        adminDashboard = System.getenv("TEXTUP_BACKEND_URL_ADMIN_DASHBOARD") ?: System.getProperty("TEXTUP_BACKEND_URL_ADMIN_DASHBOARD")
        setupAccount   = System.getenv("TEXTUP_BACKEND_URL_SETUP_ACCOUNT") ?: System.getProperty("TEXTUP_BACKEND_URL_SETUP_ACCOUNT")
        superDashboard = System.getenv("TEXTUP_BACKEND_URL_SUPER_DASHBOARD") ?: System.getProperty("TEXTUP_BACKEND_URL_SUPER_DASHBOARD")
        passwordReset  = System.getenv("TEXTUP_BACKEND_URL_PASSWORD_RESET") ?: System.getProperty("TEXTUP_BACKEND_URL_PASSWORD_RESET")
        notification   = System.getenv("TEXTUP_BACKEND_URL_NOTIFY_MESSAGE") ?: System.getProperty("TEXTUP_BACKEND_URL_NOTIFY_MESSAGE")
    }
    apiKeys {
        twilio {
            appId              = System.getenv("TEXTUP_BACKEND_TWILIO_NUMBER_APP_ID") ?: System.getProperty("TEXTUP_BACKEND_TWILIO_NUMBER_APP_ID")
            authToken          = System.getenv("TEXTUP_BACKEND_TWILIO_AUTH") ?: System.getProperty("TEXTUP_BACKEND_TWILIO_AUTH")
            available          = "unassigned"
            notificationNumber = System.getenv("TEXTUP_BACKEND_TWILIO_NOTIFICATIONS_NUMBER") ?: System.getProperty("TEXTUP_BACKEND_TWILIO_NOTIFICATIONS_NUMBER")
            sid                = System.getenv("TEXTUP_BACKEND_TWILIO_SID") ?: System.getProperty("TEXTUP_BACKEND_TWILIO_SID")
            unavailable        = "assigned"
        }
        aws {
            accessKey = System.getenv("TEXTUP_BACKEND_AWS_ACCESS_KEY") ?: System.getProperty("TEXTUP_BACKEND_AWS_ACCESS_KEY")
            secretKey = System.getenv("TEXTUP_BACKEND_AWS_SECRET_KEY") ?: System.getProperty("TEXTUP_BACKEND_AWS_SECRET_KEY")
        }
        sendGrid {
            apiKey = System.getenv("TEXTUP_BACKEND_SENDGRID_API_KEY") ?: System.getProperty("TEXTUP_BACKEND_SENDGRID_API_KEY")
            templateIds {
                invited       = "d-f142b47a7b5049e49cca2871132f4485"
                approved      = "d-6aa01fad5a6947a2b1c81743f1de3727"
                pendingOrg    = "d-04a41952661142f7a53f22b1f41b7724"
                pendingStaff  = "d-7c84d21d641841a1878516b3af446d1e"
                passwordReset = "d-9c05ebecf05f4c3695d6287866881040"
                rejected      = "d-f3db46277d3e4c45a7d9bf808250e269"
                notification  = "d-2afe7b9cd5ae44fc82debdd165ceeb05"
            }
            groupIds {
                account = 8717
            }
        }
        pusher {
            appId     = "159936"
            apiKey    = System.getenv("TEXTUP_BACKEND_PUSHER_API_KEY") ?: System.getProperty("TEXTUP_BACKEND_PUSHER_API_KEY")
            apiSecret = System.getenv("TEXTUP_BACKEND_PUSHER_API_SECRET") ?: System.getProperty("TEXTUP_BACKEND_PUSHER_API_SECRET")
        }
        mailChimp {
            apiKey = System.getenv("TEXTUP_BACKEND_MAILCHIMP_API_KEY") ?: System.getProperty("TEXTUP_BACKEND_MAILCHIMP_API_KEY")
            listIds {
                generalUpdates = System.getenv("TEXTUP_BACKEND_MAILCHIMP_GENERAL_UPDATES_LIST") ?: System.getProperty("TEXTUP_BACKEND_MAILCHIMP_GENERAL_UPDATES_LIST")
                users = System.getenv("TEXTUP_BACKEND_MAILCHIMP_USERS_LIST") ?: System.getProperty("TEXTUP_BACKEND_MAILCHIMP_USERS_LIST")
            }
        }
    }
    rest {
        marshallers {
            announcement        = [singular: "announcement", plural: "announcements"]
            contact             = [singular: "contact", plural: "contacts"]
            futureMessage       = [singular: "future-message", plural: "future-messages"]
            location            = [singular: "location", plural: "locations"]
            mediaElement        = [singular: "mediaElement", plural:  "mediaElements"]
            mediaElementVersion = [singular: "version", plural:  "versions"]
            mediaInfo           = [singular: "medium", plural:  "media"]
            mergeGroup          = [singular: "merge-group", plural: "merge-groups"]
            mergeGroupItem      = [singular: "mergeItem", plural: "mergeItems"]
            notification        = [singular: "notification", plural: "notifications"]
            notificationDetail  = [singular: "detail", plural: "details"]
            organization        = [singular: "organization", plural: "organizations"]
            ownerPolicy         = [singular: "ownerPolicy", plural: "ownerPolicies"]
            phone               = [singular: "phone", plural: "phones"]
            phoneNumber         = [singular: "number", plural: "numbers"]
            recordItem          = [singular: "record", plural: "records"]
            recordItemRequest   = [singular: "record-request", plural: "record-requests"]
            revision            = [singular: "revision", plural: "revisions"]
            schedule            = [singular: "schedule", plural: "schedules"]
            session             = [singular: "session", plural: "sessions"]
            staff               = [singular: "staff", plural: "staff"]
            tag                 = [singular: "tag", plural: "tags"]
            team                = [singular: "team", plural: "teams"]
        }
    }
}
