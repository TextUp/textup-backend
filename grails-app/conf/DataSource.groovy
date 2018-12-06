dataSource {
    pooled = true
    jmxExport = true
    // logSql = true
}
hibernate {
    // format_sql = true
    charSet = 'utf8mb4'
    characterEncoding='utf8mb4'
    useUnicode=true

    reload = false //disables recreation of the Hibernate session factory on reload, workaround for error when editing domain subclass
    cache.use_second_level_cache = true
    cache.use_query_cache = false
//    cache.region.factory_class = 'net.sf.ehcache.hibernate.EhCacheRegionFactory' // Hibernate 3
    cache.region.factory_class = 'org.hibernate.cache.ehcache.EhCacheRegionFactory' // Hibernate 4
    singleSession = true // configure OSIV singleSession mode
    flush.mode = 'manual' // OSIV session flush mode outside of transactional context
}

// environment specific settings
environments {
    development {
        dataSource {
            driverClassName = "org.h2.Driver"
            dialect = "org.textup.util.ImprovedH2Dialect"
            username = "sa"
            password = ""
            dbCreate = "create-drop" // one of 'create', 'create-drop', 'update', 'validate', ''
            // For DATABASE_TO_UPPER, see https://stackoverflow.com/a/10793358
            // For MySQL compatibility mode, see https://stokito.wordpress.com/2014/05/07/grails-mock-mysql-database-in-test-environment/
            url = "jdbc:h2:mem:devDb;MVCC=TRUE;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL;DATABASE_TO_UPPER=FALSE;IGNORECASE=TRUE"
        }
    }
    test {
        dataSource {
            driverClassName = "org.h2.Driver"
            dialect = "org.textup.util.ImprovedH2Dialect"
            username = "sa"
            password = ""
            // see `development` environment block for notes on URL options
            dbCreate = "create-drop"
            url = "jdbc:h2:mem:testDb;MVCC=TRUE;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL;DATABASE_TO_UPPER=FALSE;IGNORECASE=TRUE"
        }
    }
    production {
        dataSource {
            // even thought this isn't technically a valid value, do not leave dbCreate empty
            // in production or else any plugin can specify this setting when the configs are
            // merged for deployment, possibly resulting in loss of production data
            // see: https://stackoverflow.com/a/39271754
            dbCreate = "none" //use dbmigration plugin to manage schema changes
            driverClassName = "com.mysql.jdbc.Driver"
            dialect = "org.textup.util.MySQL5UTF8MB4InnoDBDialect"
            // url CANNOT HAVE characterEncoding=utf8 because that will
            // override our settings in /etc/mysql/my.cnf to set the character
            // encoding to utf8mb4
            url = "jdbc:mysql://localhost/prodDb?useUnicode=true"
            username = System.getenv("TEXTUP_BACKEND_DB_USERNAME") ?: System.getProperty("TEXTUP_BACKEND_DB_USERNAME")
            password = System.getenv("TEXTUP_BACKEND_DB_PASSWORD") ?: System.getProperty("TEXTUP_BACKEND_DB_PASSWORD")

            properties {
                minEvictableIdleTimeMillis = 180000
                timeBetweenEvictionRunsMillis = 180000
                numTestsPerEvictionRun = 3
                testOnBorrow = true
                testWhileIdle = true
                testOnReturn = true
                validationQuery = "SELECT 1"
            }
        }
    }
}
