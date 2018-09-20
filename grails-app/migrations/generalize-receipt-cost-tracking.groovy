databaseChangeLog = {

    changeSet(author: "ericbai", id: "1537451166858-2") {
        renameColumn(tableName: "record_item_receipt",
            oldColumnName: "num_segments",
            newColumnName: "num_billable",
            columnDataType: "integer")
    }
}
