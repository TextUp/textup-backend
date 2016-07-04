dataSource {
    pooled = true
    jmxExport = true
    // logSql = true
}
hibernate {
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
            url = "jdbc:h2:mem:devDb;MVCC=TRUE;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE"
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
            username = "prod"
            password = "textupprod"

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
