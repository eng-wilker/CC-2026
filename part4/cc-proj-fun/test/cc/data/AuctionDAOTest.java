package cc.data;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AuctionDAOTest {
	@Test
	void etagIsMappedToCosmosPropertyOnly() throws Exception {
		ObjectMapper mapper = new ObjectMapper();
		AuctionDAO auction = mapper.readValue("{\"id\":\"a\",\"_etag\":\"v1\"}", AuctionDAO.class);
		assertEquals("v1", auction.getEtag());
		JsonNode json = mapper.valueToTree(auction);
		assertEquals("v1", json.get("_etag").asText());
		assertFalse(json.has("etag"));
	}
}