package cc.utils;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Calls every endpoint of a running server and prints what fails. Only needs the base URL.
 *
 * java -cp target/classes cc.utils.TestBackend http://localhost:8080/rest
 */
public class TestBackend {

	static HttpClient http;
	static String base;
	static int passed = 0, failed = 0;

	static String userId;
	static String auctionId;

	public static void main(String[] args) throws Exception {
		base = args.length > 0 ? args[0] : "http://localhost:8080/rest";
		CookieHandler.setDefault(new CookieManager());
		http = HttpClient.newBuilder().cookieHandler(CookieHandler.getDefault()).build();

		testControl();
		testUsers();
		testMedia();
		testAuctions();
		testBids();
		testQuestions();
		testAggregationsAndSearch();
		testDeleteUser();

		System.out.printf("%n%d passed, %d failed%n", passed, failed);
		if (failed > 0)
			System.exit(1);
	}

	static void testControl() throws Exception {
		HttpResponse<String> r = get("/ctrl/version");
		check("ctrl/version returns 200", r.statusCode() == 200);
	}

	static void testUsers() throws Exception {
		userId = "u-" + System.currentTimeMillis();
		String body = "{\"id\":\"" + userId + "\",\"name\":\"Test User\",\"pwd\":\"secret\"}";

		HttpResponse<String> create = post("/user", body);
		check("POST /user returns 200", create.statusCode() == 200);
		check("POST /user does not echo the password", !create.body().contains("secret"));

		check("GET /user/{id} returns 200", get("/user/" + userId).statusCode() == 200);
		check("GET /user/unknown returns 4xx", get("/user/does-not-exist").statusCode() >= 400);

		HttpResponse<String> badAuth = post("/user/auth", "{\"user\":\"" + userId + "\",\"pwd\":\"wrong\"}");
		check("POST /user/auth rejects a bad password", badAuth.statusCode() >= 400);

		HttpResponse<String> auth = post("/user/auth", "{\"user\":\"" + userId + "\",\"pwd\":\"secret\"}");
		check("POST /user/auth accepts the right password", auth.statusCode() == 200);
		check("POST /user/auth sets a session cookie",
				auth.headers().firstValue("set-cookie").orElse("").contains("cc:session"));

		HttpResponse<String> upd = put("/user/" + userId, "{\"name\":\"Renamed\"}");
		check("PUT /user/{id} with a session returns 200", upd.statusCode() == 200);
		check("PUT /user/{id} applied the change", upd.body().contains("Renamed"));
	}

	static void testMedia() throws Exception {
		byte[] img = new byte[64 * 1024];
		new java.util.Random(42).nextBytes(img);

		HttpResponse<String> up = postBytes("/media", "image/png", img);
		boolean uploaded = check("POST /media returns 200", up.statusCode() == 200);

		if (!uploaded) {
			// no id, so there is nothing to download
			check("GET /media/{id} returns 200", false);
			check("GET /media/{id} round-trips the bytes", false);
		} else {
			String id = up.body().trim();
			HttpResponse<byte[]> down = http.send(
					HttpRequest.newBuilder(URI.create(base + "/media/" + id)).GET().build(),
					HttpResponse.BodyHandlers.ofByteArray());
			check("GET /media/{id} returns 200", down.statusCode() == 200);
			check("GET /media/{id} round-trips the bytes", java.util.Arrays.equals(img, down.body()));
		}

		check("GET /media/unknown returns 4xx", get("/media/does-not-exist").statusCode() >= 400);

		// L1: too big for a document once base64 encoded, so this should fail
		byte[] big = new byte[3 * 1024 * 1024];
		HttpResponse<String> tooBig = postBytes("/media", "image/png", big);
		check("POST /media rejects a 3 MB image (L1: the document cap)", tooBig.statusCode() >= 400);
	}

	/** The date format Auction.endTime expects. */
	static String isoPlusSeconds(long secs) {
		return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
				.withZone(java.time.ZoneOffset.UTC)
				.format(java.time.Instant.now().plusSeconds(secs));
	}

	static void testAuctions() throws Exception {
		String body = "{\"title\":\"A chair\",\"description\":\"a comfortable red chair\","
				+ "\"owner\":\"" + userId + "\",\"endTime\":\"" + isoPlusSeconds(3600) + "\","
				+ "\"minimumPrice\":10.0,\"status\":\"open\"}";

		HttpResponse<String> c = post("/auction", body);
		boolean created = check("POST /auction returns 200", c.statusCode() == 200);
		auctionId = created ? extract(c.body(), "id") : null;
		check("POST /auction returns an id", auctionId != null && !auctionId.isEmpty());

		if (auctionId == null) {
			check("GET /auction/{id} returns 200", false);
			check("PUT /auction/{id} returns 200", false);
			check("PUT /auction/{id} applied the change", false);
		} else {
			check("GET /auction/{id} returns 200", get("/auction/" + auctionId).statusCode() == 200);
			HttpResponse<String> u = put("/auction/" + auctionId, "{\"title\":\"A better chair\"}");
			check("PUT /auction/{id} returns 200", u.statusCode() == 200);
			check("PUT /auction/{id} applied the change", u.body().contains("A better chair"));
		}

		check("GET /auction returns a list", get("/auction?st=0&len=20").body().startsWith("["));
		check("GET /auction/any/recent returns a list",
				get("/auction/any/recent?st=0&len=20").body().startsWith("["));
		check("GET /user/{id}/auctions returns a list",
				get("/user/" + userId + "/auctions?st=0&len=20").body().startsWith("["));
	}

	static void testBids() throws Exception {
		if (auctionId == null) {
			for (String w : new String[] {
					"POST bid below the minimum price is rejected",
					"POST bid above the minimum price returns 200",
					"POST bid below the current highest is rejected",
					"GET /auction/{id}/bid returns a list",
					"GET /auction/{id}/bid/{bidId} returns 200",
					"the auction now carries the winning bid" })
				check(w + " (no auction)", false);
			return;
		}

		HttpResponse<String> low = post("/auction/" + auctionId + "/bid",
				"{\"user\":\"" + userId + "\",\"value\":5.0}");
		check("POST bid below the minimum price is rejected", low.statusCode() >= 400);

		HttpResponse<String> ok = post("/auction/" + auctionId + "/bid",
				"{\"user\":\"" + userId + "\",\"value\":15.0}");
		boolean bidded = check("POST bid above the minimum price returns 200", ok.statusCode() == 200);
		String bidId = bidded ? extract(ok.body(), "id") : null;

		HttpResponse<String> under = post("/auction/" + auctionId + "/bid",
				"{\"user\":\"" + userId + "\",\"value\":12.0}");
		check("POST bid below the current highest is rejected", under.statusCode() >= 400);

		check("GET /auction/{id}/bid returns a list",
				get("/auction/" + auctionId + "/bid?st=0&len=20").body().startsWith("["));
		check("GET /auction/{id}/bid/{bidId} returns 200",
				bidId != null && get("/auction/" + auctionId + "/bid/" + bidId).statusCode() == 200);
		check("the auction now carries the winning bid",
				get("/auction/" + auctionId).body().contains("15.0"));
	}

	static void testQuestions() throws Exception {
		if (auctionId == null) {
			for (String w : new String[] { "POST question returns 200", "GET questions returns a list",
					"the auction owner can reply", "a second reply is rejected" })
				check(w + " (no auction)", false);
			return;
		}
		HttpResponse<String> q = post("/auction/" + auctionId + "/question",
				"{\"user\":\"" + userId + "\",\"text\":\"Is it still available?\"}");
		boolean asked = check("POST question returns 200", q.statusCode() == 200);
		String qid = asked ? extract(q.body(), "id") : null;

		check("GET questions returns a list",
				get("/auction/" + auctionId + "/question?st=0&len=20").body().startsWith("["));

		if (qid == null) {
			check("the auction owner can reply", false);
			check("a second reply is rejected", false);
			return;
		}
		HttpResponse<String> r1 = post("/auction/" + auctionId + "/question/" + qid + "/reply",
				"{\"reply\":\"Yes it is.\"}");
		check("the auction owner can reply", r1.statusCode() == 200);

		HttpResponse<String> r2 = post("/auction/" + auctionId + "/question/" + qid + "/reply",
				"{\"reply\":\"Replying twice.\"}");
		check("a second reply is rejected", r2.statusCode() >= 400);
	}

	static void testAggregationsAndSearch() throws Exception {
		check("GET /auction/any/about-to-close returns a list",
				get("/auction/any/about-to-close?st=0&len=20").body().startsWith("["));
		check("GET /auction/any/popular returns a list",
				get("/auction/any/popular?st=0&len=20").body().startsWith("["));

		HttpResponse<String> hit = get("/auction/search?q=comfortable&st=0&len=20");
		check("search returns 200", hit.statusCode() == 200);
		check("search finds the auction by description word",
				auctionId != null && hit.body().contains(auctionId));

		HttpResponse<String> miss = get("/auction/search?q=zzzznomatch&st=0&len=20");
		check("search returns an empty list for no match", miss.body().trim().equals("[]"));
	}

	/** Must run last - it deletes the user the other tests use. */
	static void testDeleteUser() throws Exception {
		HttpResponse<String> d = http.send(HttpRequest.newBuilder(URI.create(base + "/user/" + userId))
				.DELETE().build(), HttpResponse.BodyHandlers.ofString());
		check("DELETE /user/{id} returns 200", d.statusCode() == 200);
		check("the user is gone", get("/user/" + userId).statusCode() >= 400);
		check("their auction survives, reassigned",
				auctionId != null && get("/auction/" + auctionId).body().contains("Deleted User"));
	}

	// ---- helpers ----

	static HttpResponse<String> get(String path) throws Exception {
		return http.send(HttpRequest.newBuilder(URI.create(base + path)).GET().build(),
				HttpResponse.BodyHandlers.ofString());
	}

	static HttpResponse<String> post(String path, String json) throws Exception {
		return http.send(HttpRequest.newBuilder(URI.create(base + path))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json)).build(),
				HttpResponse.BodyHandlers.ofString());
	}

	static HttpResponse<String> postBytes(String path, String contentType, byte[] body) throws Exception {
		return http.send(HttpRequest.newBuilder(URI.create(base + path))
				.header("Content-Type", contentType)
				.POST(HttpRequest.BodyPublishers.ofByteArray(body)).build(),
				HttpResponse.BodyHandlers.ofString());
	}

	static HttpResponse<String> put(String path, String json) throws Exception {
		return http.send(HttpRequest.newBuilder(URI.create(base + path))
				.header("Content-Type", "application/json")
				.PUT(HttpRequest.BodyPublishers.ofString(json)).build(),
				HttpResponse.BodyHandlers.ofString());
	}

	/** Enough to pull an id out of a flat JSON object. */
	static String extract(String json, String field) {
		int i = json.indexOf("\"" + field + "\"");
		if (i < 0) return null;
		int s = json.indexOf('"', json.indexOf(':', i) + 1);
		int e = json.indexOf('"', s + 1);
		return s < 0 || e < 0 ? null : json.substring(s + 1, e);
	}

	static boolean check(String what, boolean ok) {
		System.out.println((ok ? "  ok   " : "  FAIL ") + what);
		if (ok) passed++;
		else failed++;
		return ok;
	}
}
