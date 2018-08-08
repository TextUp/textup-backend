databaseChangeLog = {
	changeSet(author: "ericbai (generated)", id: "1533676560477-4") {
        createTable(tableName: "media_info") {
            column(autoIncrement: "true", name: "id", type: "bigint") {
                constraints(nullable: "false", primaryKey: "true", primaryKeyName: "media_infoPK")
            }

            column(name: "version", type: "bigint") {
                constraints(nullable: "false")
            }
        }
    }

    changeSet(author: "ericbai (generated)", id: "1533676560477-5") {
        createTable(tableName: "media_info_media_element") {
            column(name: "media_info_media_elements_id", type: "bigint")

            column(name: "media_element_id", type: "bigint")
        }
    }

    changeSet(author: "ericbai (generated)", id: "1533676560477-1") {
        createTable(tableName: "media_element") {
            column(autoIncrement: "true", name: "id", type: "bigint") {
                constraints(nullable: "false", primaryKey: "true", primaryKeyName: "media_elementPK")
            }

            column(name: "version", type: "bigint") {
                constraints(nullable: "false")
            }

            column(name: "send_version_id", type: "bigint") {
                constraints(nullable: "false")
            }

            column(name: "type", type: "varchar(255)") {
                constraints(nullable: "false")
            }

            column(name: "uid", type: "varchar(255)") {
                constraints(nullable: "false")
            }
        }
    }

    changeSet(author: "ericbai (generated)", id: "1533676560477-2") {
        createTable(tableName: "media_element_media_element_version") {
            column(name: "media_element_display_versions_id", type: "bigint")

            column(name: "media_element_version_id", type: "bigint")
        }
    }

    changeSet(author: "ericbai (generated)", id: "1533676560477-3") {
        createTable(tableName: "media_element_version") {
            column(autoIncrement: "true", name: "id", type: "bigint") {
                constraints(nullable: "false", primaryKey: "true", primaryKeyName: "media_elementPK")
            }

            column(name: "version", type: "bigint") {
                constraints(nullable: "false")
            }

            column(name: "key", type: "varchar(255)") {
                constraints(nullable: "false")
            }

            column(name: "media_version", type: "varchar(255)") {
                constraints(nullable: "false")
            }

            column(name: "size_in_bytes", type: "bigint") {
                constraints(nullable: "false")
            }

            column(name: "width_in_pixels", type: "integer")
        }
    }

    changeSet(author: "ericbai (generated)", id: "1533676560477-22") {
        createIndex(indexName: "FK_fxxhj8jrohr1inju5a8iunpne", tableName: "media_element") {
            column(name: "send_version_id")
        }
    }

    changeSet(author: "ericbai (generated)", id: "1533676560477-23") {
        createIndex(indexName: "FK_8x8f0u5lc460ofgi5qm4alto4", tableName: "media_element_media_element_version") {
            column(name: "media_element_display_versions_id")
        }
    }

    changeSet(author: "ericbai (generated)", id: "1533676560477-24") {
        createIndex(indexName: "FK_gl0muf3q50ic2e4lxip1gtew8", tableName: "media_element_media_element_version") {
            column(name: "media_element_version_id")
        }
    }

    changeSet(author: "ericbai (generated)", id: "1533676560477-25") {
        createIndex(indexName: "FK_9m3ymkv01qpdts6tsjaqg81v", tableName: "media_info_media_element") {
            column(name: "media_info_media_elements_id")
        }
    }

    changeSet(author: "ericbai (generated)", id: "1533676560477-26") {
        createIndex(indexName: "FK_c1o7fm5o5mnw6c1qipmlt3fe3", tableName: "media_info_media_element") {
            column(name: "media_element_id")
        }
    }

    changeSet(author: "ericbai (generated)", id: "1533676560477-14") {
        addForeignKeyConstraint(baseColumnNames: "send_version_id", baseTableName: "media_element", constraintName: "FK_fxxhj8jrohr1inju5a8iunpne", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "media_element_version", referencesUniqueColumn: "false")
    }

    changeSet(author: "ericbai (generated)", id: "1533676560477-15") {
        addForeignKeyConstraint(baseColumnNames: "media_element_display_versions_id", baseTableName: "media_element_media_element_version", constraintName: "FK_8x8f0u5lc460ofgi5qm4alto4", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "media_element", referencesUniqueColumn: "false")
    }

    changeSet(author: "ericbai (generated)", id: "1533676560477-16") {
        addForeignKeyConstraint(baseColumnNames: "media_element_version_id", baseTableName: "media_element_media_element_version", constraintName: "FK_gl0muf3q50ic2e4lxip1gtew8", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "media_element_version", referencesUniqueColumn: "false")
    }

    changeSet(author: "ericbai (generated)", id: "1533676560477-17") {
        addForeignKeyConstraint(baseColumnNames: "media_element_id", baseTableName: "media_info_media_element", constraintName: "FK_c1o7fm5o5mnw6c1qipmlt3fe3", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "media_element", referencesUniqueColumn: "false")
    }

    changeSet(author: "ericbai (generated)", id: "1533676560477-18") {
        addForeignKeyConstraint(baseColumnNames: "media_info_media_elements_id", baseTableName: "media_info_media_element", constraintName: "FK_9m3ymkv01qpdts6tsjaqg81v", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "media_info", referencesUniqueColumn: "false")
    }
}
