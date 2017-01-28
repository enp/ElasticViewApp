package ru.itx.elasticview;

import java.util.List;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.auth.AbstractUser;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.User;

public class ElasticAuth implements AuthProvider {

private Logger log = LoggerFactory.getLogger(ElasticAuth.class.getName());
	
private class ElasticUser extends AbstractUser {

	private JsonObject principal;
		
		public ElasticUser(JsonObject principal) {
			this.principal = principal;
		}
	
		public JsonObject principal() {
			return principal;
		}
	
		public void setAuthProvider(AuthProvider authProvider) {
			throw new RuntimeException("Not implemented");
		}
	
		protected void doIsPermitted(String permission, Handler<AsyncResult<Boolean>> resultHandler) {
			resultHandler.handle(Future.succeededFuture(true));
		}
		
	}

	private List<JsonObject> users;

	public void authenticate(JsonObject info, Handler<AsyncResult<User>> handler) {
		if (users != null) {
			for (JsonObject user : users) {
				if (user.getString("login").equals(info.getValue("username")) && 
					user.getString("password").equals(info.getValue("password"))) {
					log.info("User ["+info.getValue("username")+"] authenticated");
					handler.handle(Future.succeededFuture(new ElasticUser(info.mergeIn(user))));
					return;
				}
			}
			String error = "User ["+info.getValue("username")+":"+info.getValue("password")+"] not found";
			log.error(error);
			handler.handle(Future.failedFuture(new Exception(error)));
		} else {
			String error = "No users loaded";
			log.error(error);
			handler.handle(Future.failedFuture(new Exception(error)));
		}
	}

	public void setUsers(List<JsonObject> users) {
		this.users = users;
	}

}
