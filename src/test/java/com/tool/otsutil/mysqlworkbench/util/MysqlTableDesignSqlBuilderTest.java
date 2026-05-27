package com.tool.otsutil.mysqlworkbench.util;

import com.tool.otsutil.mysqlworkbench.model.request.MysqlDesignColumnRequest;
import com.tool.otsutil.mysqlworkbench.model.request.MysqlDesignIndexRequest;
import com.tool.otsutil.mysqlworkbench.model.request.MysqlTableDesignRequest;
import com.tool.otsutil.mysqlworkbench.model.view.MysqlColumnView;
import com.tool.otsutil.mysqlworkbench.model.view.MysqlDesignPreviewView;
import com.tool.otsutil.mysqlworkbench.model.view.MysqlIndexView;
import com.tool.otsutil.mysqlworkbench.model.view.MysqlTableMetadataView;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MysqlTableDesignSqlBuilderTest {

    @Test
    void shouldNotGenerateAlterStatementsWhenDesignMatchesCurrentMetadata() {
        MysqlTableMetadataView metadata = metadata(
                column("id", "bigint unsigned", false, null, true, "主键"),
                column("name", "varchar(64)", true, "", false, "")
        );
        metadata.setIndexes(Arrays.asList(
                index("PRIMARY", true, true, "id"),
                index("idx_name", false, false, "name")
        ));

        MysqlTableDesignRequest request = request(
                columnRequest("id", "bigint unsigned", null, null, false, null, true, "主键", true),
                columnRequest("name", "varchar", 64, null, true, "", false, "", false)
        );
        request.setIndexes(Collections.singletonList(indexRequest("idx_name", false, "name")));

        MysqlDesignPreviewView preview = MysqlTableDesignSqlBuilder.buildPreview(request, metadata, true);

        assertTrue(preview.getStatements().isEmpty());
    }

    @Test
    void shouldPreserveUnsignedTypeAndEmptyStringDefaultWhenBuildingAlterStatements() {
        MysqlTableMetadataView metadata = metadata(column("name", "varchar(64)", true, null, false, ""));

        MysqlTableDesignRequest request = request(
                columnRequest("id", "bigint unsigned", null, null, false, null, true, "", true),
                columnRequest("name", "varchar", 64, null, true, "", false, "", false)
        );

        MysqlDesignPreviewView preview = MysqlTableDesignSqlBuilder.buildPreview(request, metadata, true);

        assertEquals(
                "ALTER TABLE `ies_ls`.`asset_boundary` ADD COLUMN `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT",
                preview.getStatements().get(0)
        );
        assertTrue(preview.getStatements().contains(
                "ALTER TABLE `ies_ls`.`asset_boundary` MODIFY COLUMN `name` VARCHAR(64) NULL DEFAULT ''"
        ));
    }

    @Test
    void shouldGenerateTableOptionAlterStatementWhenTableOptionsChange() {
        MysqlTableMetadataView metadata = metadata(column("id", "bigint", false, null, false, ""));
        metadata.setEngine("InnoDB");
        metadata.setCharset("utf8mb4");
        metadata.setTableComment("旧注释");

        MysqlTableDesignRequest request = request(
                columnRequest("id", "bigint", null, null, false, null, false, "", false)
        );
        request.setEngine("InnoDB");
        request.setCharset("utf8mb4");
        request.setTableComment("新注释");

        MysqlDesignPreviewView preview = MysqlTableDesignSqlBuilder.buildPreview(request, metadata, true);

        assertEquals(
                Collections.singletonList("ALTER TABLE `ies_ls`.`asset_boundary` COMMENT='新注释'"),
                preview.getStatements()
        );
    }

    private MysqlTableDesignRequest request(MysqlDesignColumnRequest... columns) {
        MysqlTableDesignRequest request = new MysqlTableDesignRequest();
        request.setSchema("ies_ls");
        request.setTable("asset_boundary");
        request.setCreateMode(false);
        request.setEngine("InnoDB");
        request.setCharset("utf8mb4");
        request.setColumns(Arrays.asList(columns));
        request.setIndexes(Collections.<MysqlDesignIndexRequest>emptyList());
        return request;
    }

    private MysqlTableMetadataView metadata(MysqlColumnView... columns) {
        MysqlTableMetadataView metadata = new MysqlTableMetadataView();
        metadata.setSchema("ies_ls");
        metadata.setTable("asset_boundary");
        metadata.setTableComment("");
        metadata.setEngine("InnoDB");
        metadata.setCharset("utf8mb4");
        metadata.setColumns(Arrays.asList(columns));
        metadata.setIndexes(Collections.<MysqlIndexView>emptyList());
        return metadata;
    }

    private MysqlColumnView column(String name, String columnType, boolean nullable, String defaultValue, boolean autoIncrement, String comment) {
        MysqlColumnView column = new MysqlColumnView();
        column.setName(name);
        column.setColumnType(columnType);
        column.setNullable(nullable);
        column.setDefaultValue(defaultValue);
        column.setAutoIncrement(autoIncrement);
        column.setComment(comment);
        return column;
    }

    private MysqlDesignColumnRequest columnRequest(
            String name,
            String type,
            Integer length,
            Integer scale,
            boolean nullable,
            String defaultValue,
            boolean autoIncrement,
            String comment,
            boolean primaryKey
    ) {
        MysqlDesignColumnRequest column = new MysqlDesignColumnRequest();
        column.setName(name);
        column.setType(type);
        column.setLength(length);
        column.setScale(scale);
        column.setNullable(nullable);
        column.setDefaultValue(defaultValue);
        column.setDefaultValuePresent(defaultValue != null);
        column.setAutoIncrement(autoIncrement);
        column.setComment(comment);
        column.setPrimaryKey(primaryKey);
        return column;
    }

    private MysqlIndexView index(String name, boolean primaryKey, boolean unique, String... columns) {
        MysqlIndexView index = new MysqlIndexView();
        index.setName(name);
        index.setPrimaryKey(primaryKey);
        index.setUnique(unique);
        index.setColumns(Arrays.asList(columns));
        return index;
    }

    private MysqlDesignIndexRequest indexRequest(String name, boolean unique, String... columns) {
        MysqlDesignIndexRequest index = new MysqlDesignIndexRequest();
        index.setName(name);
        index.setUnique(unique);
        index.setColumns(Arrays.asList(columns));
        return index;
    }
}
