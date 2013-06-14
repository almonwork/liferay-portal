/**
 * Copyright (c) 2000-2013 Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.portal.verify;

import com.liferay.portal.kernel.dao.db.DB;
import com.liferay.portal.kernel.dao.db.DBFactoryUtil;
import com.liferay.portal.kernel.dao.jdbc.DataAccess;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.StringBundler;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * @author Igor Beslic
 * @author László Csontos
 */
public class VerifyDB2 extends VerifyProcess {

	@Override
	protected void doVerify() throws Exception {
		DB db = DBFactoryUtil.getDB();

		String dbType = db.getType();

		if (!dbType.equals(DB.TYPE_DB2)) {
			return;
		}

		Connection con = null;
		try {
			con = DataAccess.getUpgradeOptimizedConnection();

			_alterColumnLengths(con);

			// Certain "ALTER TABLE" statements are considered
			// "REORG-recommended" operations. After three of such operations to
			// a table will force it into a REORG-pending state and no access to
			// that table is allowed until a REORG has been performed.

			_reorgPendingTables(con);
		}
		finally {
			DataAccess.cleanUp(con);
		}
	}

	private void _alterColumnLengths(Connection con) throws Exception {
		StringBundler sb1 = new StringBundler(4);

		sb1.append("select tbname, name, coltype, length from ");
		sb1.append("sysibm.syscolumns where tbcreator = (select distinct ");
		sb1.append("current schema from sysibm.sysschemata) AND coltype = ");
		sb1.append("'VARCHAR' and length = 500");

		PreparedStatement ps = null;
		ResultSet rs = null;

		try {
			ps = con.prepareStatement(sb1.toString());

			rs = ps.executeQuery();

			while (rs.next()) {
				String tableName = rs.getString(1);

				if (!isPortalTableName(tableName)) {
					continue;
				}

				String columnName = rs.getString(2);

				StringBundler sb2 = new StringBundler(5);

				sb2.append("alter table ");
				sb2.append(tableName);
				sb2.append(" alter column ");
				sb2.append(columnName);
				sb2.append(" set data type varchar(600)");

				String sql = sb2.toString();

				if (_log.isDebugEnabled()) {
					_log.debug("Running SQL: " + sql);
				}

				runSQL(sql);
			}
		}
		finally {
			DataAccess.cleanUp(rs);
			DataAccess.cleanUp(ps);
		}
	}

	private void _reorgPendingTables(Connection con) throws Exception {
		PreparedStatement ps = null;
		ResultSet rs = null;

		try {
			ps = con.prepareStatement(
				"select tabname from sysibmadm.admintabinfo where " +
					"reorg_pending = 'Y'");

			rs = ps.executeQuery();

			while (rs.next()) {
				String tableName = rs.getString(1);

				if (!isPortalTableName(tableName)) {
					continue;
				}

				String sql = "reorg table " + tableName;

				if (_log.isDebugEnabled()) {
					_log.debug("Running SQL: " + sql);
				}

				runSQL(sql);
			}
		}
		finally {
			DataAccess.cleanUp(rs);
			DataAccess.cleanUp(ps);
		}

	}

	private static Log _log = LogFactoryUtil.getLog(VerifyDB2.class);

}