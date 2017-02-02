package ru.itx.elasticview;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;

public class ElasticAuth {

private Logger log = LoggerFactory.getLogger(ElasticAuth.class.getName());
	
	private String prefix;

	private List<JsonObject> users = new ArrayList<JsonObject>();	
	private Map<String,JsonObject> groups = new HashMap<String,JsonObject>();

	public ElasticAuth(String prefix) {
		this.prefix = prefix;
	}
	
	public void load(JsonObject data) {
		if (data != null && data.getJsonArray("hits") != null) {
			for (Object object : data.getJsonArray("hits")) {
				JsonObject document = (JsonObject)object;
				if (document.getString("_type").equals("group"))
					groups.put(document.getJsonObject("_source").getString("name"),document.getJsonObject("_source").getJsonObject("view"));
				else if (document.getString("_type").equals("user"))
					users.add(document.getJsonObject("_source"));
			}
			log.info("Users/groups are loaded");
		} else {
			log.error("Users/groups are not found");
		}
	}
	
	public void authorize(RoutingContext context) {
		String authorization = context.request().headers().get(HttpHeaders.AUTHORIZATION);
		if (authorization == null) {
			context.response().putHeader("WWW-Authenticate", "Basic realm=\"ElasticView\"").setStatusCode(401).end("Not authorized");
	    } else {
	    	String[] parts = authorization.split(" ");
	    	if (parts.length != 2 || !parts[0].equals("Basic")) {
	    		context.response().setStatusCode(400).end("Wrong authorization header format");
	    	} else {
	    		parts = new String(Base64.getDecoder().decode(parts[1])).split(":");
	    		if (parts.length != 2) {
	    			context.response().setStatusCode(400).end("Wrong authorization data");
	    		} else {
	    			for (JsonObject user : users) {
	    				if (user.getString("login").equals(parts[0]) && user.getString("password").equals(parts[1])) {
	    					boolean allowed = false;
    						String path = context.normalisedPath().replace(prefix+"/view", "view");
	    					context.put("user", user.getString("description"));
	    					if (user.getBoolean("fullAccess") || context.normalisedPath().equals(prefix+"/logged")) {
	    						allowed = true;
	    					} else {
	    						parts = path.split("/");
	    						JsonObject view = groups.get(user.getString("group"));
	    						if (parts.length == 1 && parts[0].equals("view")) {
	    							context.response().end(view.encode());
	    							allowed = true;
	    						} else if (parts.length > 1 && parts[0].equals("view")) {
	    							if (context.request().method() == HttpMethod.GET && 
	    								view.getJsonObject(parts[1]) != null && 
	    								view.getJsonObject(parts[1]).getJsonObject(parts[2]) != null)
	    								allowed = true;
	    							else if (view.getJsonObject(parts[1]) != null && 
	    								view.getJsonObject(parts[1]).getJsonObject(parts[2]) != null && 
	    								view.getJsonObject(parts[1]).getJsonObject(parts[2]).getBoolean("edit"))
	    								allowed = true;
	    						}
	    					}
	    					if (allowed) {
	    						log.trace("User ["+user.getString("login")+"] allowed : "+path);
	    						if (!context.response().ended())
	    							context.next();
	    					} else {
	    						log.trace("User ["+user.getString("login")+"] not allowed : "+path);
	    						context.response().setStatusCode(403).end("Not allowed");
	    					}
	    					return;
	    				}
	    			}
	    			log.trace("User ["+parts[0]+"] not found");
	    		}
	    	}
	    }
	}

}
