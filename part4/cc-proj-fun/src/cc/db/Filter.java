package cc.db;

public class Filter {
    private String field;
    private String operator ;
    private Object value;

    private Filter(String field, String operator, Object value) {
        this.field = field;
        this.operator = operator;
        this.value = value;
    }

    public static Filter eq(String field, Object value) {
        return new Filter(field, "=", value);
    }

    public static Filter ne(String field, Object value) {
        return new Filter(field, "!=", value);
    }

    public static Filter gt(String field, Object value) {
        return new Filter(field, ">", value);
    }

    public static Filter gte(String field, Object value) {
        return new Filter(field, ">=", value);
    }

    public static Filter lt(String field, Object value) {
        return new Filter(field, "<", value);
    }

    public static Filter lte(String field, Object value) {
        return new Filter(field, "<=", value);
    }
    public String getField() {
        return field;
    }
    public String getOperator() {
        return operator;
    }
    public Object getValue() {
        return value;
    }
    public String toQueryString( String tableName) {
        return tableName + "." + field + " " + operator + " \"" + value + "\"";
    }   

}
