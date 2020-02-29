package external;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import entity.Item;
import entity.Item.ItemBuilder;


/**
 * TicketMaster API 主要就干了一件事：
 * 1. 与TicketMaster建立http连接，
 * 2. 发起搜索请求，获得response
 * 3. 从response body里通过io流读取JSON，变成java对象JSON Array
 * 4. 通过调用小弟method把JSON Array变成内部货币List
 * 
 * 小弟：
 * List<Item> getItemList(JSONArray events)
 * 
 * 小弟的三个小小弟：
 * String getAddress(JSONObject event)
 * String getImageUrl(JSONObject event)
 * Set<String> getCategories(JSONObject event)
 * 
 * @author ringo
 *
 */
public class TicketMasterAPI {
	private static final String URL = "https://app.ticketmaster.com/discovery/v2/events.json";
	private static final String DEFAULT_KEYWORD = ""; // no restriction
	private static final String API_KEY = "w6Axa2JSdAfw2lUEAoJYR83sD2FiqKdy";

	
	/**
	 * Helper methods
	 */

	//  {
	//    "name": "laioffer",
              //    "id": "12345",
              //    "url": "www.laioffer.com",
	//    ...
	//    "_embedded": {
	//	    "venues": [
	//	        {
	//		        "address": {
	//		           "line1": "101 First St,",
	//		           "line2": "Suite 101",
	//		           "line3": "...",
	//		        },
	//		        "city": {
	//		        	"name": "San Francisco"
	//		        }
	//		        ...
	//	        },
	//	        ...
	//	    ]
	//    }
	//    ...
	//  }
	
	//小小弟一号
	private String getAddress(JSONObject event) throws JSONException {
		if (!event.isNull("_embedded")) {
			JSONObject embedded = event.getJSONObject("_embedded");
			
			if (!embedded.isNull("venues")) {
				JSONArray venues = embedded.getJSONArray("venues");
				
				for (int i = 0; i < venues.length(); ++i) {  // actually, we only care about the first venue address, so  only getting first object of venues is enough.
					JSONObject venue = venues.getJSONObject(i);//we don't need a loop indeed. but we do here in case of no data in first object address
					
					StringBuilder sb = new StringBuilder();
					
					if (!venue.isNull("address")) {
						JSONObject address = venue.getJSONObject("address");
						
						if (!address.isNull("line1")) {
							sb.append(address.getString("line1"));
						}
						if (!address.isNull("line2")) {
							sb.append(" ");
							sb.append(address.getString("line2"));
						}
						if (!address.isNull("line3")) {
							sb.append(" ");
							sb.append(address.getString("line3"));
						}
					}
					
					if (!venue.isNull("city")) {
						JSONObject city = venue.getJSONObject("city");
						
						if (!city.isNull("name")) {
							sb.append(" ");
							sb.append(city.getString("name"));
						}
					}
					
					if (!sb.toString().equals("")) {
						return sb.toString();
					}
				}
			}
		}

		return "";
	}


	// {"images": [{"url": "www.example.com/my_image.jpg"}, ...]}
	
	//小小弟二号
	private String getImageUrl(JSONObject event) throws JSONException {
		if (!event.isNull("images")) {
			JSONArray images = event.getJSONArray("images");
			
			for (int i = 0; i < images.length(); ++i) {
				JSONObject image = images.getJSONObject(i);
				
				if (!image.isNull("url")) {
					return image.getString("url");
				}
			}
		}

		return "";
	}

	// {"classifications" : [{"segment": {"name": "music"}}, ...]}
	
	//小小弟三号
	private Set<String> getCategories(JSONObject event) throws JSONException { //categories is like a tag system.
		Set<String> categories = new HashSet<>();
		if (!event.isNull("classifications")) {
			JSONArray classifications = event.getJSONArray("classifications");
			for (int i = 0; i < classifications.length(); i++) {
				JSONObject classification = classifications.getJSONObject(i);
				if (!classification.isNull("segment")) {
					JSONObject segment = classification.getJSONObject("segment");
					if (!segment.isNull("name")) {
						String name = segment.getString("name");
						categories.add(name);
					}
				}
			}
		}

		return categories;
	}

	// Convert JSONArray to a list of item objects.
	//被主逻辑search method调用的小弟。小弟下面再调用三个小弟。就是楼上三个小小弟。
	private List<Item> getItemList(JSONArray events) throws JSONException {
		List<Item> itemList = new ArrayList<>();

		for (int i = 0; i < events.length(); ++i) {
			//我们对拿到的events数据进行清洗，只留下Item类中定义的我们需要的字段：
			JSONObject event = events.getJSONObject(i);
			
			ItemBuilder builder = new ItemBuilder();
			
			if (!event.isNull("name")) {
				builder.setName(event.getString("name"));
			}
			
			if (!event.isNull("id")) {
				builder.setItemId(event.getString("id"));
			}
			
			if (!event.isNull("url")) {
				builder.setUrl(event.getString("url"));
			}
			
			if (!event.isNull("rating")) {  //there is no "rating" provided by TicketMaster any more
				builder.setRating(event.getDouble("rating"));
			}
			
			if (!event.isNull("distance")) {
				builder.setDistance(event.getDouble("distance"));
			}
			
			//下面三个都是在调用小弟
			builder.setCategories(getCategories(event));
			builder.setAddress(getAddress(event));
			builder.setImageUrl(getImageUrl(event));
			
			itemList.add(builder.build());
		}

		return itemList;
	}

	
	//主逻辑大哥，先调用了楼上的小弟（getItemList），楼上的小弟再调用楼上上的三个小弟
	//注意：这里为什么从TicketMaster拿到了JSON格式的数据还要转换成List？
	//因为直接调用此search方法的是MySQLConnection，这列返回的数据是要save到数据库里的
	//进入数据库的数据必须是string或者list<item>
	public List<Item> search(double lat, double lon, String keyword) { // change return type from JSONArray to List<Item>
        if (keyword == null) {
        	keyword = DEFAULT_KEYWORD;
        }
        
        try {
        	keyword = java.net.URLEncoder.encode(keyword, "UTF-8");// now keyword can be transmitted.
        } catch (Exception e) {
        	e.printStackTrace();
        }
        
        String geoHash = GeoHash.encodeGeohash(lat, lon, 8);
		// Make your url query part like: "apikey=12345&geoPoint=abcd&keyword=music&radius=50"
        String query = String.format("apikey=%s&geoPoint=%s&keyword=%s&radius=%s", API_KEY, geoHash, keyword, 50);


        try {
			// Open a HTTP connection between your Java application and TicketMaster based on url
			HttpURLConnection connection = (HttpURLConnection) new URL(URL + "?" + query).openConnection();
			// Set request method to GET
			connection.setRequestMethod("GET");
			// Send request to TicketMaster and get response, response code could be
			// returned directly
			// response body is saved in InputStream of connection.
			int responseCode = connection.getResponseCode();
			System.out.println("\nSending 'GET' request to URL : " + URL + "?" + query);
			System.out.println("Response Code : " + responseCode);
			// Now read response body to get events data
			BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			String inputLine;
			StringBuilder response = new StringBuilder();
			while ((inputLine = in.readLine()) != null) {
				response.append(inputLine);
			}
			in.close();
			connection.disconnect();
			
			//把从request body里读出来的JSON放进String，再把装有JSON的String转换为JSON Object
			JSONObject obj = new JSONObject(response.toString());
			if (obj.isNull("_embedded")) {
				return new ArrayList<>(); // modified
			}
			
			//为什么我们拿到JSON Object不直接返回给servlet让它回复客户端？
			//因为从ticketMaster得到的数据是生数据，需要把无用的部分剔除。
			//obj里只有embedded下的events部分才是有效的数据。
			//而events里也有许多脏数据需要清洗。但是这个清洗步骤太繁琐，于是我们交给getItemList等小弟处理。
			JSONObject embedded = obj.getJSONObject("_embedded");
			JSONArray events = embedded.getJSONArray("events");
			
			//下面这句话，又误解成分。不是为了转换成List，而是为了清洗数据。
			//把JSON Array转换成内部通行格式List。这里需要调用sibling methods
			return getItemList(events); //modified
		} catch (Exception e) {
			e.printStackTrace();
		}
		return new ArrayList<>(); //modified

	}
	
	private void queryAPI(double lat, double lon) {
		List<Item> itemList = search(lat, lon, null); // change return type from JSONArray
		try {
			for (Item item : itemList) {
				JSONObject jsonObject = item.toJSONObject();
				System.out.println(jsonObject);
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	public static void main(String[] args) {
		TicketMasterAPI tmApi = new TicketMasterAPI();
		// Mountain View, CA
		// tmApi.queryAPI(37.38, -122.08);
		// London, UK
		// tmApi.queryAPI(51.503364, -0.12);
		// Houston, TX
		tmApi.queryAPI(37.38, -122.08);
	}

}
