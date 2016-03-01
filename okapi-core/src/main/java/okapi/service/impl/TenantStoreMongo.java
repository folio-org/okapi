/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package okapi.service.impl;

import okapi.service.TenantStore;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import java.util.ArrayList;
import java.util.List;
import okapi.bean.Tenant;
import okapi.bean.TenantDescriptor;
import static okapi.util.ErrorType.*;
import okapi.util.ExtendedAsyncResult;
import okapi.util.Failure;
import okapi.util.Success;

/**
 * Mock storage for tenants. 
 * All in memory, so it starts with a clean slate every time the program starts.
 * 
 */
public class TenantStoreMongo implements TenantStore {
  //Map<String, Tenant> tenants = new HashMap<>();
  MongoClient cli;
  final private String collection = "okapi.tenants";
  private long lastTimestamp = 0;

  public TenantStoreMongo(MongoHandle mongo) {
    this.cli = mongo.getClient();
  }

  @Override
  public void insert(Tenant t,
                     Handler<ExtendedAsyncResult<String>> fut) {
    String id = t.getId(); 
    String s = Json.encodePrettily(t);
    JsonObject document = new JsonObject(s);
    document.put("_id", id);
    cli.insert(collection, document, res -> {
      if (res.succeeded()) {
        fut.handle(new Success<>(id));
      } else {
        System.out.println("TenantStoreMongo: Failed to insert " + id
          + ": " + res.cause().getMessage());
        fut.handle(new Failure<>(INTERNAL,res.cause()));
      }
    });
  }

  @Override
  public void listIds(Handler<ExtendedAsyncResult<List<String>>> fut) {
    String q = "{}";
    JsonObject jq = new JsonObject(q);
    cli.find(collection, jq, res -> {
      if (res.failed()) {
          fut.handle(new Failure<>(INTERNAL,res.cause()));
        } else {
          List<String> ids = new ArrayList<>(res.result().size());
          for (JsonObject jo : res.result()) {
            jo.remove("_id");
            final Tenant t = Json.decodeValue(jo.encode(),  Tenant.class);
            ids.add(t.getId());
          }
          System.out.println("TenantStoreMongo: listIds: " + Json.encode(ids));
          fut.handle(new Success<>(ids));
      }
    });
  }

  @Override
  public void listTenants(Handler<ExtendedAsyncResult<List<TenantDescriptor>>> fut) {
    String q = "{}";
    JsonObject jq = new JsonObject(q);
    cli.find(collection, jq, res -> {
      if (res.failed()) {
          fut.handle(new Failure<>(INTERNAL,res.cause()));
        } else {
          List<TenantDescriptor> ts = new ArrayList<>(res.result().size());
          for (JsonObject jo : res.result()) {
            jo.remove("_id");
            final Tenant t = Json.decodeValue(jo.encode(),  Tenant.class);
            ts.add(t.getDescriptor());
          }
          System.out.println("TenantStoreMongo: listIds: " + Json.encode(ts));
          fut.handle(new Success<>(ts));
      }
    });
  }


  @Override
  public void get(String id,Handler<ExtendedAsyncResult<Tenant>> fut ) {
    final String q = "{ \"_id\": \"" + id + "\"}";
    JsonObject jq = new JsonObject(q);
    System.out.println("TenantStoreMongo: Trying to get " + q);
    cli.find(collection, jq, res -> {
      if ( res.failed()) {
        System.out.println("TenantStoreMongo: get: find failed: " + res.cause().getMessage());
        fut.handle(new Failure<>(INTERNAL,res.cause()));
      } else {
        List<JsonObject> l = res.result();
        if (l.size() == 0) {
        System.out.println("TenantStoreMongo: get: not found: " + id);
          fut.handle(new Failure<>(NOT_FOUND,"Tenant " + id + " not found"));
        } else {
          JsonObject d = l.get(0);
          d.remove("_id");
          final Tenant t = Json.decodeValue(d.encode(),  Tenant.class);
          System.out.println("TenantStoreMongo: get: " + Json.encodePrettily(t));
          fut.handle(new Success<>(t));
        }
      }
    });

  }

  @Override
  public void delete(String id,Handler<ExtendedAsyncResult<Void>> fut ) {
    String q = "{ \"_id\": \"" + id + "\"}";
    JsonObject jq = new JsonObject(q);
    cli.find(collection, jq, fres -> {
      if (fres.failed()) {
          fut.handle(new Failure<>(INTERNAL,fres.cause()));
      } else {
        List<JsonObject> l = fres.result();
        if (l.size() == 0) {
          System.out.println("TeanntStoreMongo: delete. Not found " + id + ":" + q );
          fut.handle(new Failure<>(NOT_FOUND,"Tenant " + id + " not found (delete)"));
        } else {
          cli.remove(collection, jq, rres -> {
            if (rres.failed()) {
              fut.handle(new Failure<>(INTERNAL,rres.cause()));
            } else {
              fut.handle(new Success<>());
            }
          }); 
        }
      }
    } );
  }

  @Override
  public void enableModule(String id, String module,  long timestamp,
        Handler<ExtendedAsyncResult<Void>> fut ) {

    final String q = "{ \"_id\": \"" + id + "\"}";
    JsonObject jq = new JsonObject(q);
    System.out.println("TenantStoreMongo: Trying to enable module " + module + " for tenant " + id);
    cli.find(collection, jq, gres -> {
      if ( gres.failed()) {
        System.out.println("TenantStoreMongo: enable: find failed: " + gres.cause().getMessage());
        fut.handle(new Failure<>(INTERNAL,gres.cause()));
      } else {
        List<JsonObject> l = gres.result();
        if (l.size() == 0) {
          System.out.println("TenantStoreMongo: enableModule: not found: " + id);
          fut.handle(new Failure<>(NOT_FOUND,"Tenant " + id + " not found"));
        } else {
          JsonObject d = l.get(0);
          d.remove("_id");
          System.out.println("TenantStoreMongo: enableModule: d: " +Json.encode(d) );
          final Tenant t = Json.decodeValue(d.encode(),  Tenant.class);
          t.setTimestamp(timestamp);
          System.out.println("TenantStoreMongo: enableModule: " + Json.encodePrettily(t));
          t.enableModule(module);
          String s = Json.encodePrettily(t);
          JsonObject document = new JsonObject(s);
          document.put("_id", id);
          cli.save(collection, document, sres -> {
            if ( sres.failed() ) {
              System.out.println("TenantStoreMongo: enable: saving failed: " + gres.cause().getMessage());
              fut.handle(new Failure<>(INTERNAL,gres.cause()));
            } else {
              System.out.println("TenantStoreMongo: enabled module " + module + " for " + id + "ok");
              fut.handle(new Success<>());
            }
          });
        }
      }
    });
  }


}
