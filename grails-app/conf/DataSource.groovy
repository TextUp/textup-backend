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
            // driverClassName = "org.h2.Driver"
            // dialect = "org.textup.util.ImprovedH2Dialect"
            // username = "sa"
            // password = ""
            // dbCreate = "create-drop" // one of 'create', 'create-drop', 'update', 'validate', ''
            // // For DATABASE_TO_UPPER, see https://stackoverflow.com/a/10793358
            // // Do NOT add DATABASE_TO_UPPER to the `test` environment because doing so results in
            // // "ERROR hbm2ddl.SchemaUpdate  - HHH000299: Could not complete schema update"
            // url = "jdbc:h2:mem:devDb;MVCC=TRUE;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE;DATABASE_TO_UPPER=FALSE"

            // TODO
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
    test {
        dataSource {
            driverClassName = "org.h2.Driver"
            dialect = "org.textup.util.ImprovedH2Dialect"
            username = "sa"
            password = ""
            dbCreate = "update"
            url = "jdbc:h2:mem:testDb;MVCC=TRUE;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE"
        }
    }
    production {
        dataSource {
            // dbCreate = "update" //use dbmigration plugin to manage schema changes
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
