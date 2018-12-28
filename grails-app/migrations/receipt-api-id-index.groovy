databaseChangeLog = {

	changeSet(author: "ericbai (generated)", id: "1545971043671-1") {
		createIndex(indexName: "ix_record_item_receipt_api_id", tableName: "record_item_receipt") {
			column(name: "api_id")
		}
	}
}
