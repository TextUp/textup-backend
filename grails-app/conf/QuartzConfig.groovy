
quartz {
    autoStartup = true
    jdbcStore = false
    waitForJobsToCompleteOnShutdown = true
    exposeSchedulerInRepository = false

    props {
        scheduler.skipUpdateCheck = true
    }
}

environments {
    test {
        quartz {
            autoStartup = false
        }
    }
    production {
        quartz {
            jdbcStore = true

            props {
                threadPool.'class' = 'org.quartz.simpl.SimpleThreadPool'
                threadPool.threadCount = 10

                jobStore.'class' = 'org.quartz.impl.jdbcjobstore.JobStoreTX'
                jobStore.driverDelegateClass = 'org.quartz.impl.jdbcjobstore.StdJDBCDelegate'

                jobStore.useProperties = false
                jobStore.tablePrefix = 'QRTZ_'

                plugin.shutdownhook.'class' = 'org.quartz.plugins.management.ShutdownHookPlugin'
                plugin.shutdownhook.cleanShutdown = true
            }
        }
    }
}
