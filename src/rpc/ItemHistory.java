package rpc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import db.DBConnection;
import db.DBConnectionFactory;
import entity.Item;

/**
 * Servlet implementation class ItemHistory
 */
@WebServlet("/history")
public class ItemHistory extends HttpServlet {
	private static final long serialVersionUID = 1L;
       
    /**
     * @see HttpServlet#HttpServlet()
     */
    public ItemHistory() {
        super();
    }

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		//doGet通过request.getParameter从url的query里获得参数，并拿着参数到数据里搜索
		String userId = request.getParameter("user_id");
		JSONArray array = new JSONArray();

		//对数据库进行SELECT操作，分别从history和items表里
		//在同包不同类调用的时候，可以直接 类名.方法() 调用，无需创建对象。在不同包调用的时候，需要引包再调用。
		DBConnection conn = DBConnectionFactory.getConnection();
		Set<Item> items = conn.getFavoriteItems(userId);
		
		//把list转换成json ob，并放进json array里
		for (Item item : items) {
			JSONObject obj = item.toJSONObject();
			try {
				obj.append("favorite", true);
				
			} catch (JSONException e) {
				e.printStackTrace();
			}
			array.put(obj);
		}
		RpcHelper.writeJsonArray(response, array);

	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		try {
			//不同与doGet操作，doPost和doDelete，并不是从url中获取query参数到数据库搜索
			//post和delete操作都是把信息放到request body里的
			//因此我们要通过RPCHelper把request body里的JSON数据拿出来
			JSONObject input = RpcHelper.readJsonObject(request);
			String userId = input.getString("user_id");		
			JSONArray array = input.getJSONArray("favorite");
			
			
			//把JSON array 转换成 list
			List<String> itemIds = new ArrayList<>();
			for (int i = 0; i < array.length(); ++i) {
				itemIds.add(array.get(i).toString());
			}
			
			//调用DAO层，通过DAO对数据库进行CRUD操作
			//这里省略了controller和service层，直接通过servlet对话DAO
			//对history表的INSERT操作
			DBConnection conn = DBConnectionFactory.getConnection();
			conn.setFavoriteItems(userId, itemIds);
			conn.close();
			
			RpcHelper.writeJsonObject(response,
					new JSONObject().put("result", "SUCCESS"));
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	/**
	 * @see HttpServlet#doDelete(HttpServletRequest, HttpServletResponse)
	 */
	protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		try {
			JSONObject input = RpcHelper.readJsonObject(request);
			String userId = input.getString("user_id");
			JSONArray array = input.getJSONArray("favorite");
			
			
			List<String> itemIds = new ArrayList<>();
			for (int i = 0; i < array.length(); ++i) {
				itemIds.add(array.get(i).toString());
			}
			
			//对history表的DELETE操作
			DBConnection conn = DBConnectionFactory.getConnection();
			conn.unsetFavoriteItems(userId, itemIds);
			conn.close();
			
			RpcHelper.writeJsonObject(response,
					new JSONObject().put("result", "SUCCESS"));
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

}
