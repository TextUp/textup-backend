databaseChangeLog = {
    changeSet(author: "ericbai", id: "1539956368803-17") {
        grailsChange {
            change {
                sql.executeUpdate("""
                    UPDATE token
                    SET max_num_access = 1
                    WHERE type = 'CALL_DIRECT_MESSAGE'
                """)
            }
            rollback {
                sql.executeUpdate("""
                    UPDATE token
                    SET max_num_access = NULL
                    WHERE type = 'CALL_DIRECT_MESSAGE'
                """)
            }
        }
    }
}
