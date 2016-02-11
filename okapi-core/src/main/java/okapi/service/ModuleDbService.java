/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.service;

import okapi.bean.ModuleDescriptor;
import io.vertx.core.Vertx;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import okapi.bean.ModuleInstance;

public class ModuleDbService {
  ModuleService moduleService;
  MongoClient cli;

  final private Vertx vertx;
  final String collection = "okapi.modules";

  public ModuleDbService(Vertx vertx, ModuleService moduleService) {
    this.vertx = vertx;
    this.moduleService = moduleService;

    JsonObject opt = new JsonObject().
            put("host", "127.0.0.1").
            put("port", 27017);
    this.cli = MongoClient.createShared(vertx, opt);
  }

  public void init(RoutingContext ctx) {
    cli.dropCollection(collection, res -> {
      if (res.succeeded()) {
        ctx.response().setStatusCode(204).end();
      } else {
        ctx.response().setStatusCode(500).end(res.cause().getMessage());
      }
    });
  }
  
  public void create(RoutingContext ctx) {
    try {
      final ModuleDescriptor md = Json.decodeValue(ctx.getBodyAsString(),
              ModuleDescriptor.class);
      String s = Json.encodePrettily(md);
      JsonObject document = new JsonObject(s);
      document.put("_id", document.getString("id"));
      cli.insert(collection, document, res -> {
        if (res.succeeded()) {
          moduleService.create(ctx);
        } else {
          System.out.println("create failred " + res.cause().getLocalizedMessage());
          ctx.response().setStatusCode(500).end(res.cause().getMessage());
        }
      });
    } catch (DecodeException ex) {
      ctx.response().setStatusCode(400).end(ex.getMessage());
    }
  }

  public void get(RoutingContext ctx) {
    final String id = ctx.request().getParam("id");
    final String q = "{ \"id\": \"" + id + "\"}";
    JsonObject jq = new JsonObject(q);
    cli.find(collection, jq, res -> {
      if (res.succeeded()) {
        List<JsonObject> l = res.result();
        if (l.size() > 0) {
          JsonObject d = l.get(0);
          d.remove("_id");
          ctx.response().setStatusCode(200).end(Json.encodePrettily(d));
        } else {
          ctx.response().setStatusCode(404).end();
        }
      } else {
        ctx.response().setStatusCode(500).end(res.cause().getMessage());
      }
    });
  }

  public void list(RoutingContext ctx) {
    String q = "{}";
    JsonObject jq = new JsonObject(q);
    cli.find(collection, jq, res -> {
      List<String> ids = new ArrayList<>(res.result().size());
      if (res.succeeded()) {
        for (JsonObject jo : res.result()) {
          ids.add(jo.getString("id"));
        }
        ctx.response().setStatusCode(200).end(Json.encodePrettily(ids));
      } else {
        ctx.response().setStatusCode(500).end(res.cause().getMessage());
      }
    });
    // moduleService.list(ctx);
  }

  public void delete(RoutingContext ctx) {
    final String id = ctx.request().getParam("id");
    String q = "{ \"id\": \"" + id + "\"}";
    JsonObject jq = new JsonObject(q);
    cli.find(collection, jq, res -> {
      if (res.succeeded()) {
        List<JsonObject> l = res.result();
        if (l.size() > 0) {
          cli.remove(collection, jq, res2 -> {
            if (res.succeeded()) {
              moduleService.delete(ctx);
              // ctx.response().setStatusCode(204).end();
            } else {
              ctx.response().setStatusCode(500).end(res.cause().getMessage());
            }
          });
        } else {
          ctx.response().setStatusCode(404).end();
        }
      } else {
        ctx.response().setStatusCode(500).end(res.cause().getMessage());
      }
    });
  }
} // class
