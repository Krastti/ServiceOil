package db.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Переносит существующие строки на числовые ключи, не пересоздавая прикладные данные. */
public class V7__integer_primary_keys extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        var db = context.getConnection();
        // Проверяем диапазоны до первого DDL, в том числе для тестовой H2 без транзакционного DDL.
        for (var table : List.of("import_run", "admin_account", "vehicle_template")) {
            scalar(db, "select cast(id as integer) from " + table);
            if (number(db, "select coalesce(max(id), 0) from " + table) >= Integer.MAX_VALUE) {
                throw new SQLException("INTEGER identity exhausted: " + table);
            }
        }
        dropForeignKeys(db, "regional_price");
        dropPrimaryKey(db, "regional_price");
        for (var table : List.of("region", "fuel_type", "data_source")) dropPrimaryKey(db, table);

        execute(db, "alter table data_source rename column id to code");
        for (var table : List.of("region", "fuel_type", "data_source")) {
            addIdentity(db, table);
            execute(db, "alter table " + table + " add constraint uq_" + table + "_code unique (code)");
        }

        execute(db, "alter table regional_price rename column source_id to source_code");
        for (var column : List.of("source_id", "region_id", "fuel_type_id")) {
            execute(db, "alter table regional_price add column " + column + " integer");
        }
        execute(db, "update regional_price set source_id = (select s.id from data_source s where s.code = regional_price.source_code), "
                + "region_id = (select r.id from region r where r.code = regional_price.region_code), "
                + "fuel_type_id = (select f.id from fuel_type f where f.code = regional_price.fuel_code)");
        addIdentity(db, "regional_price");
        for (var column : List.of("source_id", "region_id", "fuel_type_id")) {
            execute(db, "alter table regional_price alter column " + column + " set not null");
        }
        execute(db, "drop index if exists regional_price_history");
        for (var column : List.of("source_code", "region_code", "fuel_code")) {
            execute(db, "alter table regional_price drop column " + column);
        }
        execute(db, "alter table regional_price add constraint uq_regional_price_observation "
                + "unique (source_id, region_id, fuel_type_id, observed_on)");
        execute(db, "alter table regional_price add constraint fk_price_source foreign key (source_id) references data_source(id)");
        execute(db, "alter table regional_price add constraint fk_price_region foreign key (region_id) references region(id)");
        execute(db, "alter table regional_price add constraint fk_price_fuel foreign key (fuel_type_id) references fuel_type(id)");

        execute(db, "alter table regional_price_archive rename column source_id to source_code");
        addIdentity(db, "regional_price_archive");
        execute(db, "alter table regional_price_archive add constraint uq_archive_observation "
                + "unique (source_code, region_code, fuel_code, observed_on)");
        execute(db, "alter table import_run rename column source_id to source_code");

        execute(db, "alter table vehicle_template add column fuel_type_id integer");
        execute(db, "update vehicle_template set fuel_type_id = (select f.id from fuel_type f where f.code = vehicle_template.fuel_code)");
        execute(db, "alter table vehicle_template alter column fuel_type_id set not null");
        execute(db, "alter table vehicle_template add constraint fk_vehicle_fuel foreign key (fuel_type_id) references fuel_type(id)");
        // DROP COLUMN удаляет локальный CHECK на прежнем строковом столбце; HTTP-валидация кодов сохраняется.
        execute(db, "alter table vehicle_template drop column fuel_code");

        boolean postgres = db.getMetaData().getDatabaseProductName().equals("PostgreSQL");
        for (var table : List.of("import_run", "admin_account", "vehicle_template")) {
            execute(db, "alter table " + table + " alter column id set data type integer");
            if (postgres) {
                // Изменение типа столбца само по себе не сужает тип его identity sequence.
                execute(db, "alter sequence " + identitySequence(db, table) + " as integer");
            }
            long next = Math.max(1, number(db, "select coalesce(max(id), 0) from " + table) + 1);
            execute(db, "alter table " + table + " alter column id restart with " + next);
        }
    }

    private static void addIdentity(Connection db, String table) throws SQLException {
        execute(db, "alter table " + table + " add column id integer generated by default as identity");
        execute(db, "alter table " + table + " add constraint pk_" + table + " primary key (id)");
    }

    private static String metadataName(Connection db, String name) throws SQLException {
        return db.getMetaData().storesUpperCaseIdentifiers() ? name.toUpperCase(Locale.ROOT) : name;
    }

    private static void dropForeignKeys(Connection db, String table) throws SQLException {
        var names = new ArrayList<String>();
        try (var keys = db.getMetaData().getImportedKeys(db.getCatalog(), db.getSchema(), metadataName(db, table))) {
            while (keys.next()) names.add(keys.getString("FK_NAME"));
        }
        for (var name : names.stream().distinct().toList()) {
            execute(db, "alter table " + table + " drop constraint " + quote(name));
        }
    }

    private static void dropPrimaryKey(Connection db, String table) throws SQLException {
        String name;
        try (var keys = db.getMetaData().getPrimaryKeys(db.getCatalog(), db.getSchema(), metadataName(db, table))) {
            if (!keys.next()) throw new SQLException("Missing primary key: " + table);
            name = keys.getString("PK_NAME");
        }
        execute(db, "alter table " + table + " drop constraint " + quote(name));
    }

    private static String quote(String name) { return "\"" + name.replace("\"", "\"\"") + "\""; }

    private static String identitySequence(Connection db, String table) throws SQLException {
        try (var statement = db.createStatement();
             var result = statement.executeQuery("select pg_get_serial_sequence('" + table + "', 'id')")) {
            result.next();
            // Сервер возвращает уже корректно квалифицированное SQL-имя последовательности.
            return result.getString(1);
        }
    }

    private static long number(Connection db, String sql) throws SQLException {
        try (var statement = db.createStatement(); var result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }

    private static void scalar(Connection db, String sql) throws SQLException {
        try (var statement = db.createStatement(); var result = statement.executeQuery(sql)) {
            while (result.next()) result.getInt(1);
        }
    }

    private static void execute(Connection db, String sql) throws SQLException {
        try (var statement = db.createStatement()) { statement.execute(sql); }
    }
}
