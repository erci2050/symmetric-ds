/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU General Public License, version 3.0 (GPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU General Public License,
 * version 3.0 (GPLv3) along with this library; if not, see
 * <http://www.gnu.org/licenses/>.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jumpmind.db.platform.greenplum;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.DatabaseMetaDataWrapper;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.postgresql.PostgreSqlDdlReader;

public class GreenplumDdlReader extends PostgreSqlDdlReader {

    public GreenplumDdlReader(IDatabasePlatform platform) {
        super(platform);
    }

    protected void setDistributionKeys(Connection connection, Table table, String schema)
            throws SQLException {

        // get the distribution keys for segments
        StringBuilder query = new StringBuilder();

        query.append("select                                        ");
        query.append("   t.relname,                                 ");
        query.append("   a.attname                                  ");
        query.append("from                                          ");
        query.append("   pg_class t,                                ");
        query.append("   pg_namespace n,                            ");
        query.append("   pg_attribute a,                            ");
        query.append("   gp_distribution_policy p                   ");
        query.append("where                                         ");
        query.append("   n.oid = t.relnamespace and                 ");
        query.append("   p.localoid = t.oid and                     ");
        query.append("   a.attrelid = t.oid and                     ");
        query.append("   a.attnum = any(p.attrnums) and             ");
        query.append("   n.nspname = ? and                          ");
        query.append("   t.relname = ?                              ");

        PreparedStatement prepStmt = connection.prepareStatement(query.toString());

        try {
            // set the schema parm in the query
            prepStmt.setString(1, schema);
            prepStmt.setString(2, table.getName());
            ResultSet rs = prepStmt.executeQuery();

            // for every row, set the distributionKey for the corresponding
            // columns
            while (rs.next()) {
                Column column = table.findColumn(rs.getString(2).trim(), getPlatform().getDdlBuilder()
                        .isDelimitedIdentifierModeOn());
                if (column != null) {
                    column.setDistributionKey(true);
                }
            }
            rs.close();
        } finally {
            if (prepStmt != null) {
                prepStmt.close();
            }
        }
    }

    @Override
    protected Table readTable(Connection connection, DatabaseMetaDataWrapper metaData,
            Map<String, Object> values) throws SQLException {
        Table table = super.readTable(connection, metaData, values);
        setDistributionKeys(connection, table, metaData.getSchemaPattern());
        return table;
    }
}
