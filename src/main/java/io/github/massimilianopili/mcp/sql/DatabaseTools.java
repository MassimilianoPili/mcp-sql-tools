package io.github.massimilianopili.mcp.sql;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DatabaseTools {

    private final Map<String, JdbcTemplate> registry;

    public DatabaseTools(Map<String, JdbcTemplate> jdbcTemplateRegistry) {
        this.registry = jdbcTemplateRegistry;
    }

    @Tool(name = "db_query", description = "Executes a read-only SELECT query on the database and returns results as a list of rows. Only SELECT queries are allowed.")
    public List<Map<String, Object>> executeQuery(
            @ToolParam(description = "SQL SELECT query to execute") String query,
            @ToolParam(description = "Database name (from db_list_databases). If omitted, uses the first available.", required = false) String database) {
        String trimmed = query.trim().toUpperCase();
        if (!trimmed.startsWith("SELECT")) {
            throw new IllegalArgumentException("Solo query SELECT permesse");
        }
        return getJdbc(database).queryForList(query);
    }

    @Tool(name = "db_tables", description = "Lists all database tables with their columns, data types and ordinal position. Automatically adapts the query to the DB type (H2, Oracle, PostgreSQL, MySQL).")
    public List<Map<String, Object>> listTables(
            @ToolParam(description = "Database name (from db_list_databases). If omitted, uses the first available.", required = false) String database,
            @ToolParam(description = "Schema/owner name (optional). For Oracle: e.g. PAGAMENTI_DEV. For H2: e.g. PUBLIC", required = false) String schema) {
        JdbcTemplate jdbc = getJdbc(database);
        String dbType = detectDbTypeFromJdbc(jdbc);

        switch (dbType) {
            case "oracle":
                return listTablesOracle(jdbc, schema);
            case "mysql":
                return listTablesMysql(jdbc, schema);
            default:
                // H2, PostgreSQL, e altri standard ANSI
                return listTablesStandard(jdbc, schema);
        }
    }

    @Tool(name = "db_count", description = "Counts rows in a table, optionally with a WHERE filter")
    public Map<String, Object> countRows(
            @ToolParam(description = "Table name") String tableName,
            @ToolParam(description = "Optional WHERE condition, e.g. price > 100", required = false) String whereClause,
            @ToolParam(description = "Database name (from db_list_databases). If omitted, uses the first available.", required = false) String database) {
        if (!tableName.matches("[a-zA-Z_][a-zA-Z0-9_.]*")) {
            throw new IllegalArgumentException("Nome tabella non valido: " + tableName);
        }
        String sql = "SELECT COUNT(*) as total FROM " + tableName;
        if (whereClause != null && !whereClause.isBlank()) {
            sql += " WHERE " + whereClause;
        }
        return getJdbc(database).queryForMap(sql);
    }

    @Tool(name = "db_list_databases", description = "Lists the SQL databases configured in the MCP server. Each name can be used as the 'database' parameter in other DB tools.")
    public List<String> listDatabases() {
        return new ArrayList<>(registry.keySet());
    }

    @Tool(name = "db_list_schemas", description = "Lists accessible schemas/owners in the specified database. For Oracle returns table owners, for H2/PostgreSQL returns information_schema schemas.")
    public List<Map<String, Object>> listSchemas(
            @ToolParam(description = "Database name (from db_list_databases). If omitted, uses the first available.", required = false) String database) {
        JdbcTemplate jdbc = getJdbc(database);
        String dbType = detectDbTypeFromJdbc(jdbc);

        switch (dbType) {
            case "oracle":
                return jdbc.queryForList(
                        "SELECT DISTINCT owner as schema_name FROM all_tables ORDER BY owner");
            case "mysql":
                return jdbc.queryForList(
                        "SELECT schema_name FROM information_schema.schemata ORDER BY schema_name");
            default:
                return jdbc.queryForList(
                        "SELECT schema_name FROM information_schema.schemata ORDER BY schema_name");
        }
    }

    // --- Metodi privati ---

    private JdbcTemplate getJdbc(String database) {
        if (database == null || database.isBlank()) {
            // Usa il primo database disponibile
            return registry.values().iterator().next();
        }
        JdbcTemplate jdbc = registry.get(database);
        if (jdbc == null) {
            throw new IllegalArgumentException(
                    "Database '" + database + "' non trovato. Disponibili: " + registry.keySet());
        }
        return jdbc;
    }

    private String detectDbTypeFromJdbc(JdbcTemplate jdbc) {
        try {
            String url = jdbc.getDataSource().getConnection().getMetaData().getURL();
            return MultiDataSourceConfig.detectDbType(url);
        } catch (Exception e) {
            return "unknown";
        }
    }

    private List<Map<String, Object>> listTablesStandard(JdbcTemplate jdbc, String schema) {
        String effectiveSchema = (schema != null && !schema.isBlank()) ? schema : "PUBLIC";
        return jdbc.queryForList(
                "SELECT table_name, column_name, data_type, ordinal_position " +
                "FROM information_schema.columns " +
                "WHERE table_schema = ? " +
                "ORDER BY table_name, ordinal_position",
                effectiveSchema);
    }

    private List<Map<String, Object>> listTablesOracle(JdbcTemplate jdbc, String schema) {
        if (schema != null && !schema.isBlank()) {
            return jdbc.queryForList(
                    "SELECT table_name, column_name, data_type, column_id as ordinal_position " +
                    "FROM all_tab_columns " +
                    "WHERE owner = ? " +
                    "ORDER BY table_name, column_id",
                    schema.toUpperCase());
        } else {
            // Senza schema, mostra le tabelle dell'utente corrente
            return jdbc.queryForList(
                    "SELECT table_name, column_name, data_type, column_id as ordinal_position " +
                    "FROM user_tab_columns " +
                    "ORDER BY table_name, column_id");
        }
    }

    private List<Map<String, Object>> listTablesMysql(JdbcTemplate jdbc, String schema) {
        if (schema != null && !schema.isBlank()) {
            return jdbc.queryForList(
                    "SELECT table_name, column_name, data_type, ordinal_position " +
                    "FROM information_schema.columns " +
                    "WHERE table_schema = ? " +
                    "ORDER BY table_name, ordinal_position",
                    schema);
        } else {
            return jdbc.queryForList(
                    "SELECT table_name, column_name, data_type, ordinal_position " +
                    "FROM information_schema.columns " +
                    "WHERE table_schema = DATABASE() " +
                    "ORDER BY table_name, ordinal_position");
        }
    }
}
