package db.mysql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import db.DBConnection;
import entity.Item;
import entity.Item.ItemBuilder;
import external.TicketMasterAPI;


//这个类相当于spring mvc里的DAO层。直接对数据库进行CRUD操作。
//类中的method，基本也遵循CRUD原则
//这里省略了controller和service层，直接通过servlet对话DAO
public class MySQLConnection implements DBConnection {

	private Connection conn;

	public MySQLConnection() {
		try {
			Class.forName("com.mysql.jdbc.Driver").newInstance();
			conn = DriverManager.getConnection(MySQLDBUtil.URL);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void close() {
		// TODO Auto-generated method stub
		if (conn != null) {
			try {
				conn.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

	}

	//注意这里接收的是item id的list，就算只收藏一个item，也要把item放进list里传进来。
	@Override
	public void setFavoriteItems(String userId, List<String> itemIds) {

		if (conn == null) {
			return;
		}
		
		try {
			String sql = "INSERT IGNORE INTO history (user_id, item_id) VALUES (?, ?)";
			PreparedStatement stmt = conn.prepareStatement(sql);
			for (String itemId : itemIds) {
				stmt.setString(1, userId);
				stmt.setString(2, itemId);
				stmt.execute();
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}

	}

	@Override
	public void unsetFavoriteItems(String userId, List<String> itemIds) {

		if (conn == null) {
			return;
		}
		
		try {
			String sql = "DELETE FROM history WHERE user_id = ? AND item_id = ?";
			PreparedStatement stmt = conn.prepareStatement(sql);
			for (String itemId : itemIds) {
				stmt.setString(1, userId);
				stmt.setString(2, itemId);
				stmt.execute();
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}

	}

	//因为不能直接通过user id直接找到favorite items
	//它们在两张不同的table上
	//因此拆分成两个methods：
	//1. 通过 userId 在history表上 获取 itemId
	//2. 通过返回的itemId 在 item 的表里获得 item的相关信息
	
	//知道这里为什么返回的是set不是list？因为set又contains方法
	@Override
	public Set<String> getFavoriteItemIds(String userId) {
		
		if (conn == null) {
			return new HashSet<>();
		}
		
		Set<String> favoriteItemIds = new HashSet<>();
		
		try {
			String sql = "SELECT item_id from history where user_id = ?";
			PreparedStatement stmt = conn.prepareStatement(sql);
			stmt.setString(1, userId);
			ResultSet rs = stmt.executeQuery();
			while (rs.next()) {
				String itemId = rs.getString("item_id");
				favoriteItemIds.add(itemId);
			}
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		return favoriteItemIds;

	}

	@Override
	public Set<Item> getFavoriteItems(String userId) {
		// TODO Auto-generated method stub
		if (conn == null) {
			return new HashSet<>();
		}
		
		Set<Item> favoriteItems = new HashSet<>();
		
		//根据user id获得收藏列表中每个item的id，从数据库取出根据id 取出item（包含一个item的各种信息）
		//然后把取出的item信息封装成对象。
		Set<String> itemIds = getFavoriteItemIds(userId);
		
		try {
			String sql = "SELECT * FROM items WHERE item_id = ?";
			//preparedStatement，和普通的Statement的区别，为了防住sql注入攻击。
			PreparedStatement stmt = conn.prepareStatement(sql);
			for (String itemId : itemIds) {
				//1代表上面sql语句中第一个“？”代表的占位符。
				stmt.setString(1, itemId);
				
				//返回一个set
				ResultSet rs = stmt.executeQuery();
				
				ItemBuilder builder = new ItemBuilder();
				//遍历set，把数据库中的一条条record转化成一个个java对象
				//然后把这些转换成的java对象放进set里，返回。
				while (rs.next()) {
					builder.setItemId(rs.getString("item_id"));
					builder.setName(rs.getString("name"));
					builder.setAddress(rs.getString("address"));
					builder.setImageUrl(rs.getString("image_url"));
					builder.setUrl(rs.getString("url"));
					builder.setCategories(getCategories(itemId));
					builder.setDistance(rs.getDouble("distance"));
					builder.setRating(rs.getDouble("rating"));
					
					favoriteItems.add(builder.build());
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		return favoriteItems;

	}

	@Override
	public Set<String> getCategories(String itemId) {
		
		if (conn == null) {
			return null;
		}
		Set<String> categories = new HashSet<>();
		try {
			String sql = "SELECT category from categories WHERE item_id = ? ";
			PreparedStatement statement = conn.prepareStatement(sql);
			statement.setString(1, itemId);
			ResultSet rs = statement.executeQuery();
			while (rs.next()) {
				categories.add(rs.getString("category"));
			}
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
		return categories;

	}

	
	//除了这个method是与外部建立连接，发起搜索请求，其他的method都是对内部数据库进行操作
	@Override
	public List<Item> searchItems(double lat, double lon, String term) {
		
		TicketMasterAPI tmAPI = new TicketMasterAPI();
		//经过JSON对象到Item对象，我们把从TicketMaster拿到的原始数据进行清洗，放进Item类里
		List<Item> items = tmAPI.search(lat, lon, term);
		for (Item item : items) {
			saveItem(item);
		}
		return items;
	}

	@Override
	public void saveItem(Item item) {
		

		if (conn == null) {
			return;
		}
		
		try {
			// SQL injection
			// Example:
			// SELECT * FROM users WHERE username = '<username>' AND password = '<password>';
			//
			// sql = "SELECT * FROM users WHERE username = '" + username + "'
			//       AND password = '" + password + "'"
			//
			// username: aoweifjoawefijwaoeifj
			// password: 123456' OR '1' = '1
			//
			// SELECT * FROM users WHERE username = 'aoweifjoawefijwaoeifj' AND password = '123456' OR '1' = '1'
			String sql = "INSERT IGNORE INTO items VALUES (?, ?, ?, ?, ?, ?, ?)";
			PreparedStatement stmt = conn.prepareStatement(sql);
			stmt.setString(1, item.getItemId());
			stmt.setString(2, item.getName());
			stmt.setDouble(3, item.getRating());
			stmt.setString(4, item.getAddress());
			stmt.setString(5, item.getImageUrl());
			stmt.setString(6, item.getUrl());
			stmt.setDouble(7, item.getDistance());
			stmt.execute();
			
			sql = "INSERT IGNORE INTO categories VALUES (?, ?)";
			stmt = conn.prepareStatement(sql);
			for (String category : item.getCategories()) {
				stmt.setString(1, item.getItemId());
				stmt.setString(2, category);
				stmt.execute(); 
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}

	}

	@Override
	public String getFullname(String userId) {
		
		if (conn == null) {
			return null;
		}
		String name = "";
		try {
			String sql = "SELECT first_name, last_name from users WHERE user_id = ?";
			PreparedStatement statement = conn.prepareStatement(sql);
			statement.setString(1, userId);
			ResultSet rs = statement.executeQuery();
			if (rs.next()) {
				name = String.join(" ", rs.getString("first_name"), rs.getString("last_name"));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return name;

	}

	@Override
	public boolean verifyLogin(String userId, String password) {
		// TODO Auto-generated method stub
		if (conn == null) {
			return false;
		}
		try {
			String sql = "SELECT user_id from users WHERE user_id = ? and password = ?";
			PreparedStatement statement = conn.prepareStatement(sql);
			statement.setString(1, userId);
			statement.setString(2, password);
			ResultSet rs = statement.executeQuery();
			if (rs.next()) {
				return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;

	}

}
