package com.tool.otsutil.mysqlworkbench.util;

import com.tool.otsutil.exception.CustomException;
import com.tool.otsutil.model.common.AppHttpCodeEnum;

import java.util.Collection;
import java.util.regex.Pattern;

public final class MysqlIdentifierUtils {

    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[A-Za-z0-9_$]+$");

    private MysqlIdentifierUtils() {
    }

    public static String quoteIdentifier(String identifier) {
        validateIdentifier(identifier, "标识符不合法");
        return "`" + identifier + "`";
    }

    public static String qualifyTable(String schema, String table) {
        return quoteIdentifier(schema) + "." + quoteIdentifier(table);
    }

    public static void validateIdentifier(String identifier, String message) {
        if (identifier == null || !IDENTIFIER_PATTERN.matcher(identifier).matches()) {
            throw new CustomException(AppHttpCodeEnum.PARAM_INVALID, message);
        }
    }

    public static void validateIdentifiers(Collection<String> identifiers, String message) {
        if (identifiers == null) {
            throw new CustomException(AppHttpCodeEnum.PARAM_INVALID, message);
        }
        for (String identifier : identifiers) {
            validateIdentifier(identifier, message);
        }
    }
}
