package algorithm;


import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import db.DBConnection;
import db.DBConnectionFactory;
import entity.Item;

//这个推荐算法究竟做了什么？
/**
 * userId、lat、lon，入参跟search操作没有区别。
 * 但是内部执行的逻辑不同。
 * 
 * 1. 先连接数据库，
 * 2. 从数据库里拿到favorite items
 * 3. 把每个item对应的tags（categories）放进set里，变成一个装有所有你喜欢的tag的set大麻袋
 * 4. 装麻袋的时候，若有重复的tag装入，权重+1
 * 5. 根据权重排序（把set换成list，放进Collections.sort()方法中）
 * 6. 拿到根据权重大小排序的categories清单，我们根据每个category进行search操作
 * 7. category相当于search中的term/keyword入参
 * 8. 每个category都搜出一堆item，跟favorite Items那堆比一下，如果不在收藏里，也不在visited items里，就放进filtered list里
 * 9. 每个category在ticketMaster里搜到的items，遍历完一遍，分拣出filtered items，
 * 10. 进入下一个category之前，把分拣出来的filtered items根据距离远近排一个序，然后放进recommended list里
 * 10. 直到category list中每一个category都被遍历完
 * 
 * 总体就是，按tag的优先级搜索events，每个tag的events又按照距离优先级排序。
 * 先tag，后距离。
 * 推荐原则：根据favorite items获得你喜欢的tags，根据tag搜索events，但是
 * 			如果在favorite列表里，就剔除。我们要推荐新的events
 * 
 * @author ringo
 *
 */

public class GeoRecommendation {
	public List<Item> recommendItems(String userId, double lat, double lon) {
		List<Item> recommendedItems = new ArrayList<>();
		
		//调用数据库，对history表进行SELECT操作
		DBConnection conn = DBConnectionFactory.getConnection();
		
		// Step 1 Get all favorite items
		Set<String> favoriteItemIds = conn.getFavoriteItemIds(userId);

		// Step 2 Get all categories of favorite items, sort by count
		Map<String, Integer> allCategories = new HashMap<>();
		for (String itemId : favoriteItemIds) {
			Set<String> categories = conn.getCategories(itemId);
			for (String category : categories) {
				allCategories.put(category, allCategories.getOrDefault(category, 0) + 1);
			}
		}
		
		List<Entry<String, Integer>> categoryList =
				new ArrayList<Entry<String, Integer>>(allCategories.entrySet());
		
		Collections.sort(categoryList, new Comparator<Entry<String, Integer>>() {
			
			@Override
			public int compare(Entry<String, Integer> o1, Entry<String, Integer> o2) {
				return Integer.compare(o2.getValue(), o1.getValue());
			}
		});

		// Step 3, do search based on category, filter out favorited events, sort by
		// distance
		Set<Item> visitedItems = new HashSet<>();
		
		for (Entry<String, Integer> category : categoryList) {
			List<Item> items = conn.searchItems(lat, lon, category.getKey());
			List<Item> filteredItems = new ArrayList<>();
			for (Item item : items) {
				if (!favoriteItemIds.contains(item.getItemId())
						&& !visitedItems.contains(item)) {
					filteredItems.add(item);
				}
			}
			
			Collections.sort(filteredItems, new Comparator<Item>() {
				@Override
				public int compare(Item item1, Item item2) {
					return Double.compare(item1.getDistance(), item2.getDistance());
				}
			});
			
			visitedItems.addAll(items);
			recommendedItems.addAll(filteredItems);
		}
		
		return recommendedItems;


	  }

	
}
