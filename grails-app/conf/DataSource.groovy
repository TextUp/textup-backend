dataSource {
    pooled = true
    jmxExport = true
    // // For debugging only
    // logSql = true
}
hibernate {
    charSet = "utf8mb4"
    characterEncoding = "utf8mb4"
    useUnicode = true

    // // For debugging only
    // format_sql = true // only affects if we are logging SQL (see `logSql` option above OR log4j config in `Config.groovy`)
    // generate_statistics = true

    // NO BATCHING SUPPORT: because Grails uses the native identity generator to generate ids for inserts,
    // Hibernate disables batching inserts. We need to change to another identity provider before
    // we can using the batching features of Hibernate.
    // See: https://vladmihalcea.com/hibernate-identity-sequence-and-table-sequence-generator/
    // jdbc.batch_size = 30
    // jdbc.batch_versioned_data = true

    // Helps prevent deadlocks in highly concurrent settings.
    // see: https://docs.jboss.org/hibernate/orm/4.3/manual/en-US/html_single/#configuration-optional
    order_updates = true

    //disables recreation of the Hibernate session factory on reload, workaround for error when editing domain subclass
    reload = false

    // Note that the second-level cache  requires look-ups solely via the id of the entity.
    // Therefore, we usually choose to cache at the service level using Grail's cache plugin.
    // Specifically, we cache the RecordItemReceipt apiId to its current status so that we can see
    // if we need to update the stored status value in the status callback webhook. This will reduce
    // the number of database calls to check to see what the stored status is and also will
    // reduce the number of updates to the database, hopefully decreasing the number of
    // OptimisticLockingExceptions we are seeing in the logs.
    cache.use_second_level_cache = true
    // Second level cache holds entities and query cache is a special "region" of the second
    // level cache that holds queries and parameters. We only use the query cache for very special use
    // cases because any write to a table (insert/update/delete) clears out all queries for that
    // table in the query cache. This makes the query cache suitable for holding dynamic finder queries
    // for `CustomAccountDetails`s. The reason why we need the query cache in addition to the second
    // level cache becasue the second-level cache requires look-up directly from id. When we are
    // determining which authToken to use based on the provided accountId, we are using a dynamic
    // finder based on the accountId. Therefore, we need to cache this query so that we don't
    // keep on hitting the database.
    cache.use_query_cache = true
    cache.region.factory_class = "org.hibernate.cache.ehcache.EhCacheRegionFactory" // Hibernate 4

    singleSession = true // configure OSIV singleSession mode
    flush.mode = "manual" // OSIV session flush mode outside of transactional context
}

// environment specific settings
environments {
    development {
        dataSource {
            driverClassName = "org.h2.Driver"
            dialect = "org.textup.override.ImprovedH2Dialect"
            username = "sa"
            password = ""
            dbCreate = "create"
            // For DATABASE_TO_UPPER, see https://stackoverflow.com/a/10793358
            // For MySQL compatibility mode, see https://stokito.wordpress.com/2014/05/07/grails-mock-mysql-database-in-test-environment/
            url = "jdbc:h2:mem:devDb;MVCC=TRUE;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL;DATABASE_TO_UPPER=FALSE;IGNORECASE=TRUE"
        }
    }
    test {
        dataSource {
            driverClassName = "org.h2.Driver"
            dialect = "org.textup.override.ImprovedH2Dialect"
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
            dialect = "org.textup.override.MySQL5UTF8MB4InnoDBDialect"
            // [NOTE] url CANNOT HAVE characterEncoding=utf8 because that will
            // override our settings in /etc/mysql/my.cnf to set the character
            // encoding to utf8mb4
            // [NOTE] to prevent mysql from using the system timezone, we force mysql to use
            // the server timezone, which we set to UTC in `BootStrap.groovy`
            // see https://stackoverflow.com/a/7610174
            url = "jdbc:mysql://localhost/prodDb?useUnicode=true&useLegacyDatetimeCode=false&serverTimezone=UTC"
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
