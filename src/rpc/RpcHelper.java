package rpc;

import java.io.BufferedReader;
import java.io.PrintWriter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONArray;
import org.json.JSONObject;

public class RpcHelper {
	
		//下面两个方法都是把JSON object或array 写成 JSON 然后发送会客户端
	
		// Writes a JSONObject to http response.
		public static void writeJsonObject(HttpServletResponse response, JSONObject obj) {
			try {
				response.setContentType("application/json");
				response.addHeader("Access-Control-Allow-Origin", "*");
				PrintWriter out = response.getWriter();
				out.print(obj);
				out.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		// Writes a JSONArray to http response.
		public static void writeJsonArray(HttpServletResponse response, JSONArray array) {
			try {
				response.setContentType("application/json");
				response.addHeader("Access-Control-Allow-Origin", "*");
				PrintWriter out = response.getWriter();
				out.print(array);
				out.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}


		
		//读取request body里的JSON读取成string，再把string转换成java对象JSON Object
		public static JSONObject readJsonObject(HttpServletRequest request) {
			StringBuilder sb = new StringBuilder();
			try {
				BufferedReader reader = request.getReader();
				String line = null;
				while ((line = reader.readLine()) != null) {
					sb.append(line);
				}
				reader.close();
				return new JSONObject(sb.toString());
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			return null;

		}

}
