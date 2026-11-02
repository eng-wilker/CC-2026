package cc.serverless;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;

import cc.ops.AuctionOps;
import cc.utils.Result;

public class CacheRefreshFunction {
	@FunctionName("auction-cache-refresh")
	public void refresh(
			@TimerTrigger(name = "timerInfo", schedule = "0 */1 * * * *") String timerInfo,
			ExecutionContext context) {
		Result<Void> result = AuctionOps.getInstance().refreshListingCaches();
		if (!result.isOK()) {
			context.getLogger().severe("Auction cache refresh failed with status " + result.error());
			throw new IllegalStateException("Auction cache refresh failed with status " + result.error());
		}
		context.getLogger().info("Auction listing caches refreshed");
	}
}
