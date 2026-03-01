# MCP SQL Tools

Spring Boot starter providing MCP tools for SQL database operations. Supports multi-database environments with Oracle, H2, PostgreSQL, and MySQL.

## Installation

```xml
<dependency>
    <groupId>io.github.massimilianopili</groupId>
    <artifactId>mcp-sql-tools</artifactId>
    <version>0.0.1</version>
</dependency>
```

Requires Java 17+ and Spring AI 1.0.0+.

## Tools

| Tool | Description |
|------|-------------|
| `db_query` | Execute read-only SQL queries with parameterized input |
| `db_tables` | List tables and columns in the current database |
| `db_count` | Count rows in a table with optional WHERE clause |
| `db_list_databases` | List all configured database connections |
| `db_list_schemas` | List schemas in the current database |

## Configuration

```properties
# Single database
MCP_DB_URL=jdbc:postgresql://localhost:5432/mydb
MCP_DB_DRIVER=org.postgresql.Driver
MCP_DB_USER=postgres
MCP_DB_PASSWORD=secret

# Multi-database (pattern: MCP_DB_{NAME}_URL, MCP_DB_{NAME}_DRIVER, etc.)
MCP_DB_NAMES=PRODUCTION,STAGING
MCP_DB_PRODUCTION_URL=jdbc:oracle:thin:@host:1521/prod
MCP_DB_PRODUCTION_DRIVER=oracle.jdbc.OracleDriver
MCP_DB_PRODUCTION_USER=app
MCP_DB_PRODUCTION_PASSWORD=secret
```

Always active when a JDBC DataSource is available (default: H2 in-memory).

## How It Works

- Uses `@Tool` (Spring AI) for synchronous MCP tool methods
- Auto-configured via `SqlToolsAutoConfiguration` with `@ConditionalOnClass(JdbcTemplate.class)`
- Multi-datasource registry creates HikariCP DataSources dynamically from `MCP_DB_NAMES`

## Requirements

- Java 17+
- Spring Boot 3.4+
- Spring AI 1.0.0+

## License

[MIT License](LICENSE)
