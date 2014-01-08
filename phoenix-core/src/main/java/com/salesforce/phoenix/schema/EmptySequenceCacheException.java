package com.salesforce.phoenix.schema;

import java.sql.SQLException;

import com.salesforce.phoenix.exception.SQLExceptionCode;
import com.salesforce.phoenix.exception.SQLExceptionInfo;

public class EmptySequenceCacheException extends SQLException {
    private static final long serialVersionUID = 1L;
    private static SQLExceptionCode ERROR_CODE = SQLExceptionCode.EMPTY_SEQUENCE_CACHE;

    public EmptySequenceCacheException() {
        this(null,null);
    }

    public EmptySequenceCacheException(String schemaName, String tableName) {
        super(new SQLExceptionInfo.Builder(ERROR_CODE).setSchemaName(schemaName).setTableName(tableName).build().toString(),
                ERROR_CODE.getSQLState(), ERROR_CODE.getErrorCode(), null);
    }

}
