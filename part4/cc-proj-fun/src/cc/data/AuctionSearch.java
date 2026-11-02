package cc.data;

import cc.utils.Result;

public interface AuctionSearch {
	Result<String[]> search(String query, int offset, int limit);
}
