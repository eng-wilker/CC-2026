package cc.data;

public class DBTypeDAO {
    private String _ts; 
    private String _rid;
    private String id;
    public DBTypeDAO() {
    }
    public DBTypeDAO( String id) {
        this.id = id;
    }
    public DBTypeDAO( String _ts, String id) {
        this._ts = _ts;
        this.id = id;
    }
    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }
    public String get_ts() {
        return _ts;
    }
    public void set_ts(String _ts) {
        this._ts = _ts;
    }
	public String get_rid() {
		return _rid;
	}
	public void set_rid(String _rid) {
		this._rid = _rid;
    }
    public String getPartKey() {
        return id;
    }
    @Override
    public String toString() {
        return "DBTypeDAO [_ts=" + _ts + ", _rid=" + _rid + ", id=" + id + "]";
    }

}
