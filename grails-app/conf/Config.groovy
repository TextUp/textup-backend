import org.textup.Helpers
import org.textup.Result
import org.textup.validator.MediaInfo

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
        textup.apiKeys.twilio.sid = System.getenv("TWILIO_SID") ?: System.getProperty("TWILIO_SID")
        textup.apiKeys.twilio.authToken = System.getenv("TWILIO_AUTH") ?: System.getProperty("TWILIO_AUTH")
        textup.apiKeys.twilio.appId="AP762342f6263b687fdc60c12dc9fbded8"

        grails.logging.jul.usebridge = true
        grails.plugin.databasemigration.updateOnStart = false
        grails.serverURL = System.getenv("SERVER_URL") ?: System.getProperty("SERVER_URL")
        grails.plugin.console.baseUrl="http://localhost:8080/console"
    }
    test {
        textup.apiKeys.twilio.sid = System.getenv("TWILIO_TEST_SID") ?: System.getProperty("TWILIO_TEST_SID")
        textup.apiKeys.twilio.authToken = System.getenv("TWILIO_TEST_AUTH") ?: System.getProperty("TWILIO_TEST_AUTH")
    }
    production {
        textup.apiKeys.twilio.sid = System.getenv("TWILIO_SID") ?: System.getProperty("TWILIO_SID")
        textup.apiKeys.twilio.authToken = System.getenv("TWILIO_AUTH") ?: System.getProperty("TWILIO_AUTH")
        textup.apiKeys.twilio.appId=System.getenv("TWILIO_NUMBER_APP_ID") ?: (System.getProperty("TWILIO_NUMBER_APP_ID") ?: "APe80c7d1e8a78963cde8c95785fdd8c9d")

        grails.logging.jul.usebridge = false
        grails.serverURL = System.getenv("SERVER_URL") ?: (System.getProperty("SERVER_URL") ?: "https://dev.textup.org")
        grails.plugin.databasemigration.updateOnStart = true
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
    }
}

// log4j configuration
log4j.main = {
    // Example of changing the log pattern for the default console appender:
    //
    //appenders {
    //    console name:'stdout', layout:pattern(conversionPattern: '%c{2} %m%n')
    //}
    // // Enable Hibernate SQL logging with param values
    // trace 'org.hibernate.type'
    // debug 'org.hibernate.SQL'
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
           'net.bull.javamelody',                           // Melody monitoring
           'grails.app.jobs'                                // Quartz jobs
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
    '/monitoring/**':                 ['ROLE_USER', 'ROLE_ADMIN'],
    '/dbconsole/**':                  ['ROLE_USER', 'ROLE_ADMIN'],
    "/console/**":                    ['ROLE_USER', 'ROLE_ADMIN'],
    "/plugins/console*/**":           ['ROLE_USER', 'ROLE_ADMIN'],
    '/restApiDoc':                    ['permitAll'],
    '/restApiDoc/**':                 ['permitAll'],
    '/reset':                         ['permitAll'],
    '/reset/**':                      ['permitAll'],
    '/validate':                      ['permitAll'],
    '/validate/**':                   ['permitAll'],
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
grails.plugins.restapidoc.customClassName="org.textup.rest.CustomResponseDoc"
grails.plugins.restapidoc.outputFileGeneration="web-app/restapidoc.json"
grails.plugins.restapidoc.outputFileReading="restapidoc.json"
//General developer documentation
grails.doc.title = "TextUp"
grails.doc.subtitle = "A getting started guide to the backend and frontend codebases that make up the TextUp application"
grails.doc.authors = "Eric Bai"
grails.doc.license = "MIT"

grails.gorm.default.constraints = {
    // 65535 bytes max size `text` column type divided by 4 bytes per character ut8mb4
    textSqlType(maxSize:15000)
    serializedMedia(blank:true, nullable:true, maxSize:15000)
    media(validator: { MediaInfo mInfo ->
        // mInfo may be null because serializedMedia is optional
        if (mInfo && !mInfo.validate()) {
            Result res = Helpers.resultFactory.failWithValidationErrors(mInfo.errors)
            ["errors", res.errorMessages]
        }
    })
}

textup {
    maxNumText = 500 //max number of recipients to text
    imageCompressionQualty = 0.5

    defaultMax = 10 //default max during pagination
    largestMax = 100 //largest max allowed during pagination

    resetTokenSize = 25
    verifyTokenSize = 5
    numTimesAccessNotification = 3 // number of times a notification is allowed to be accessed

    media {
        bucketName = System.getenv("STORAGE_BUCKET_NAME") ?: (System.getProperty("STORAGE_BUCKET_NAME") ?: "staging-media-textup-org")
        cdn {
            root = System.getenv("CDN_ROOT") ?: (System.getProperty("CDN_ROOT") ?: "staging-media.textup.org")
            keyId = System.getenv("CDN_KEY_ID") ?: (System.getProperty("CDN_KEY_ID") ?: "APKAJQNKTKVCOMQFPVTA")
            privateKeyPath = System.getenv("CDN_PRIVATE_KEY_PATH") ?: (System.getProperty("CDN_PRIVATE_KEY_PATH") ?: "/cloudfront-2017-private.der")
        }
    }
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
        adminDashboard = System.getenv("URL_ADMIN_DASHBOARD") ?: (System.getProperty("URL_ADMIN_DASHBOARD") ?: "https://app.textup.org/#/admin")
        setupAccount = System.getenv("URL_SETUP_ACCOUNT") ?: (System.getProperty("URL_SETUP_ACCOUNT") ?: "https://app.textup.org/#/setup")
        superDashboard = System.getenv("URL_SUPER_DASHBOARD") ?: (System.getProperty("URL_SUPER_DASHBOARD") ?: "https://v2.textup.org/super")
        passwordReset = System.getenv("URL_PASSWORD_RESET") ?: (System.getProperty("URL_PASSWORD_RESET") ?: "https://app.textup.org/#/reset?token=")
        notifyMessage = System.getenv("URL_NOTIFY_MESSAGE") ?: (System.getProperty("URL_NOTIFY_MESSAGE") ?: "https://app.textup.org/#/notify?token=")
    }

    //On Tomcat7 on EC2, these are set in /etc/tomcat7/tomcat7.conf
    //in the format: JAVA_OPTS="${JAVA_OPTS} -Dkey=value"
    apiKeys {
        twilio {
            unavailable="assigned"
            available="unassigned"
            notificationNumber = System.getenv("TWILIO_NOTIFICATIONS_NUMBER") ?: System.getProperty("TWILIO_NOTIFICATIONS_NUMBER")
        }
        aws {
            accessKey = System.getenv("AWS_ACCESS_KEY") ?: System.getProperty("AWS_ACCESS_KEY")
            secretKey = System.getenv("AWS_SECRET_KEY") ?: System.getProperty("AWS_SECRET_KEY")
        }
        sendGrid {
            apiKey = System.getenv("SENDGRID_API_KEY") ?: System.getProperty("SENDGRID_API_KEY")
            templateIds {
                invited = "87c36daa-c4a8-4f33-8539-22005cd252a8"
                approved = "7346d586-4466-4982-abfe-7a891e51c0a1"
                pendingOrg = "0f13e96f-e673-481f-95a0-8d1044b5afe9"
                pendingStaff = "9bb56ba3-902f-4207-8e75-3b2ebf347a51"
                passwordReset = "b4e228a7-2b1e-4f80-a4dc-42eb3205c538"
                rejected = "d3287be3-b427-4010-9591-71d7aaf5f040"
            }
            groupIds {
                account = 8717
            }
        }
        pusher {
            appId = "159936"
            apiKey = System.getenv("PUSHER_API_KEY") ?: System.getProperty("PUSHER_API_KEY")
            apiSecret = System.getenv("PUSHER_API_SECRET") ?: System.getProperty("PUSHER_API_SECRET")
        }
        reCaptcha {
            verifyEndpoint = "https://www.google.com/recaptcha/api/siteverify"
            secret = System.getenv("RECAPTCHA_SECRET") ?: System.getProperty("RECAPTCHA_SECRET")
        }
    }
    rest {
        defaultLabel = "default" //default is to link to relationships
        v1 {
            announcement = [singular:"announcement", plural:"announcements"]
            availableNumber = [singular:"number", plural:"numbers"]
            contact = [singular:"contact", plural:"contacts"]
            futureMessage = [singular:"future-message", plural:"future-messages"]
            location = [singular:"location", plural:"locations"]
            mediaElement = [singular:"mediaElement", plural: "mediaElements"]
            mediaInfo = [singular:"medium", plural: "media"]
            mergeGroup = [singular:"contact", plural: "contacts"]
            notification = [singular:"notification", plural:"notifications"]
            notificationStatus = [singular:"notification-status", plural:"notification-statuses"]
            organization = [singular:"organization", plural:"organizations"]
            phone = [singular:"phone", plural:"phones"]
            receipt = [singular:"receipt", plural:"receipts"]
            record = [singular:"record", plural:"records"]
            result = [singular:"result", plural:"results"]
            revision = [singular:"revision", plural:"revisions"]
            schedule = [singular:"schedule", plural:"schedules"]
            session = [singular:"session", plural:"sessions"]
            staff = [singular:"staff", plural:"staff"]
            staffPolicyAvailability = [singular:"availability", plural:"availabilities"]
            tag = [singular:"tag", plural:"tags"]
            team = [singular:"team", plural:"teams"]
        }
    }
}
