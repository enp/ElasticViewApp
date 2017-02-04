package ru.itx.elasticview;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;

public class ElasticAuth {

	private Logger log = LoggerFactory.getLogger(ElasticAuth.class.getName());
	
	private String prefix;
	
	private HttpClient client;

	private List<JsonObject> users = new ArrayList<JsonObject>();	
	private Map<String,JsonObject> groups = new HashMap<String,JsonObject>();

	public ElasticAuth(String prefix, HttpClient client) {
		this.prefix = prefix;
		this.client = client;
		load();
	}
	
	public void load() {
		client.get("/.elasticview/_search?size=1000", response -> {
			response.bodyHandler( body -> {
				JsonObject data = body.toJsonObject().getJsonObject("hits");
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
		    }).exceptionHandler(error -> {
		      log.error("Users/groups read error : "+error.getMessage());
		    });
		}).exceptionHandler(error -> {
			log.error("Users/groups request error : "+error.getMessage());
		}).end();
		
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
	    					context.put("user", user);
	    					if (user.getBoolean("fullAccess") || context.normalisedPath().equals(prefix+"/logged")) {
	    						allowed = true;
	    					} else {
	    						parts = path.split("/");
	    						JsonObject view = groups.get(user.getString("group"));
	    						if (parts.length == 1 && parts[0].equals("view")) {
	    							context.response().end(view.encode());
	    							allowed = true;
	    						} else if (parts.length > 2 && parts[0].equals("view")) {
	    							HttpMethod method = context.request().method();
	    							String index = parts[1];
	    							String type  = parts[2];
	    							if (method == HttpMethod.GET && 
	    								view.getJsonObject(index) != null && 
	    								view.getJsonObject(index).getJsonObject(type) != null) {
	    								allowed = true;
	    							} else if (parts.length == 4 && 
		    							view.getJsonObject(index) != null && 
	    								view.getJsonObject(index).getJsonObject(type) != null && 
	    								view.getJsonObject(index).getJsonObject(type).getJsonObject("actions") != null) {
	    								JsonObject actions = view.getJsonObject(index).getJsonObject(type).getJsonObject("actions");
	    								String id = parts[3];
	    								if (method == HttpMethod.POST && actions.getBoolean("save") && !id.isEmpty()) {
	    									// no check for editFields
	    									allowed = true;
	    								} else if (method == HttpMethod.POST && actions.getBoolean("copy") && id.isEmpty()) {
	    									// no check for editFields
	    									allowed = true;
	    								} else if (method == HttpMethod.DELETE && actions.getBoolean("delete") && !id.isEmpty()) {
	    									allowed = true;
	    								}
	    							}
	    						}
	    					}
	    					if (allowed) {
	    						log.trace("User ["+user.getString("login")+"] allowed : "+context.request().method()+" "+path);
	    						if (!context.response().ended())
	    							context.next();
	    					} else {
	    						log.trace("User ["+user.getString("login")+"] not allowed : "+context.request().method()+" "+path);
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
