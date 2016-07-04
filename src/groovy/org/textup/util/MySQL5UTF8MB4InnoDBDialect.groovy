package org.textup.util

import org.hibernate.dialect.MySQL5InnoDBDialect

// Sets the default charset to utf8mb4

class MySQL5UTF8MB4InnoDBDialect extends MySQL5InnoDBDialect {

	@Override
	public String getTableTypeString() {
		" ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC"
	}
}
