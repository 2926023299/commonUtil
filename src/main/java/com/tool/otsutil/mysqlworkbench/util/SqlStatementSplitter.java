package com.tool.otsutil.mysqlworkbench.util;

import java.util.ArrayList;
import java.util.List;

public final class SqlStatementSplitter {

    private SqlStatementSplitter() {
    }

    public static List<String> split(String sql) {
        List<String> statements = new ArrayList<String>();
        if (sql == null || sql.trim().isEmpty()) {
            return statements;
        }

        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inBacktick = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;

        for (int index = 0; index < sql.length(); index++) {
            char currentChar = sql.charAt(index);
            char nextChar = index + 1 < sql.length() ? sql.charAt(index + 1) : '\0';
            char previousChar = index > 0 ? sql.charAt(index - 1) : '\0';

            if (inLineComment) {
                if (currentChar == '\n') {
                    inLineComment = false;
                }
                continue;
            }

            if (inBlockComment) {
                if (currentChar == '*' && nextChar == '/') {
                    inBlockComment = false;
                    index++;
                }
                continue;
            }

            if (!inSingleQuote && !inDoubleQuote && !inBacktick) {
                if (currentChar == '-' && nextChar == '-') {
                    inLineComment = true;
                    index++;
                    continue;
                }
                if (currentChar == '#') {
                    inLineComment = true;
                    continue;
                }
                if (currentChar == '/' && nextChar == '*') {
                    inBlockComment = true;
                    index++;
                    continue;
                }
            }

            if (currentChar == '\'' && !inDoubleQuote && !inBacktick && previousChar != '\\') {
                inSingleQuote = !inSingleQuote;
            } else if (currentChar == '"' && !inSingleQuote && !inBacktick && previousChar != '\\') {
                inDoubleQuote = !inDoubleQuote;
            } else if (currentChar == '`' && !inSingleQuote && !inDoubleQuote) {
                inBacktick = !inBacktick;
            }

            if (currentChar == ';' && !inSingleQuote && !inDoubleQuote && !inBacktick) {
                appendStatement(statements, current);
                current.setLength(0);
                continue;
            }

            current.append(currentChar);
        }

        appendStatement(statements, current);
        return statements;
    }

    private static void appendStatement(List<String> statements, StringBuilder current) {
        String statement = current.toString().trim();
        if (!statement.isEmpty()) {
            statements.add(statement);
        }
    }
}
