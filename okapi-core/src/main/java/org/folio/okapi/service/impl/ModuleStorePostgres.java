package org.folio.okapi.service.impl;

import org.folio.okapi.service.ModuleStore;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.sql.UpdateResult;
import java.util.ArrayList;
import java.util.List;
import org.folio.okapi.bean.ModuleDescriptor;
import static org.folio.okapi.common.ErrorType.*;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Success;

public class ModuleStorePostgres implements ModuleStore {

  private final Logger logger = LoggerFactory.getLogger("okapi");
  private final PostgresHandle pg;
  private final String jsonColumn = "modulejson";
  private final String idSelect = jsonColumn + "->>'id' = ?";
  private final String idIndex = jsonColumn + "->'id'";

  public ModuleStorePostgres(PostgresHandle pg) {
    this.pg = pg;
  }

  public void resetDatabase(Storage.InitMode initMode, Handler<ExtendedAsyncResult<Void>> fut) {
    pg.getConnection(gres -> {
      if (gres.failed()) {
        logger.fatal("resetDatabase: getConnection() failed: "
                + gres.cause().getMessage());
        fut.handle(new Failure<>(gres.getType(), gres.cause()));
      } else {
        final SQLConnection conn = gres.result();
        String dropSql = "DROP TABLE IF EXISTS modules";
        conn.query(dropSql, dres -> {
          if (dres.failed()) {
            logger.fatal(dropSql + ": " + gres.cause().getMessage());
            fut.handle(new Failure<>(gres.getType(), gres.cause()));
            pg.closeConnection(conn);
          } else {
            logger.debug("Dropped the module table");
            if (initMode != Storage.InitMode.INIT) {
              fut.handle(new Success<>());
              return;
            }
            String createSql = "create table modules ( "
                    + jsonColumn + " JSONB NOT NULL )";
            conn.query(createSql, cres -> {
              if (cres.failed()) {
                logger.fatal(createSql + ": " + gres.cause().getMessage());
                fut.handle(new Failure<>(gres.getType(), gres.cause()));
                pg.closeConnection(conn);
              } else {
                String createSql1 = "CREATE UNIQUE INDEX module_id ON " + ""
                        + "modules USING btree((" + idIndex + "))";
                conn.query(createSql1, res -> {
                  if (cres.failed()) {
                    logger.fatal(createSql1 + ": " + gres.cause().getMessage());
                    fut.handle(new Failure<>(gres.getType(), gres.cause()));
                  } else {
                    logger.debug("Intitialized the module table");
                    fut.handle(new Success<>());
                  }
                  pg.closeConnection(conn);
                });
              }
            });
          }
        });
      }
    });
  } // resetDatabase

  private void insert(SQLConnection conn, ModuleDescriptor md, Handler<ExtendedAsyncResult<Void>> fut) {
    String sql = "INSERT INTO modules ( " + jsonColumn + " ) VALUES (?::JSONB)";
    String s = Json.encode(md);
    JsonObject doc = new JsonObject(s);
    JsonArray jsa = new JsonArray();
    jsa.add(doc.encode());
    conn.queryWithParams(sql, jsa, ires -> {
      if (ires.failed()) {
        logger.fatal("insert failed: " + ires.cause().getMessage());
        fut.handle(new Failure<>(INTERNAL, ires.cause()));
      } else {
        fut.handle(new Success<>());
      }
    });
  }

  @Override
  public void insert(ModuleDescriptor md,
          Handler<ExtendedAsyncResult<String>> fut) {
    logger.debug("insert");
    pg.getConnection(gres -> {
      if (gres.failed()) {
        logger.fatal("insert: getConnection() failed: " + gres.cause().getMessage());
        fut.handle(new Failure<>(gres.getType(), gres.cause()));
      } else {
        SQLConnection conn= gres.result();
        insert(conn, md, res -> {
          if (res.failed()) {
            fut.handle(new Failure<>(res.getType(), res.cause()));
          } else {
            fut.handle(new Success<>(md.getId()));
          }
          pg.closeConnection(conn);
        });
      }
    });
  }

  @Override
  public void update(ModuleDescriptor md,
          Handler<ExtendedAsyncResult<String>> fut) {
    logger.debug("update");
    final String id = md.getId();
    pg.getConnection(gres -> {
      if (gres.failed()) {
        logger.fatal("get: getConnection() failed: " + gres.cause().getMessage());
        fut.handle(new Failure<>(gres.getType(), gres.cause()));
      } else {
        SQLConnection conn = gres.result();
        String sql = "INSERT INTO modules (" + jsonColumn + ") VALUES (?::JSONB)"
                + " ON CONFLICT ((" + idIndex + ")) DO UPDATE SET " + jsonColumn + "= ?::JSONB";
        String s = Json.encode(md);
        JsonObject doc = new JsonObject(s);
        JsonArray jsa = new JsonArray();
        jsa.add(doc.encode());
        jsa.add(doc.encode());
        conn.updateWithParams(sql, jsa, sres -> {
          if (sres.failed()) {
            logger.fatal("update failed: " + sres.cause().getMessage());
            fut.handle(new Failure<>(INTERNAL, sres.cause()));
          } else {
            UpdateResult result = sres.result();
            if (result.getUpdated() > 0)
              fut.handle(new Success<>(id));
            else
              fut.handle(new Success<>(id));
          }
          pg.closeConnection(conn);
        });
      }
    });
  }

  @Override
  public void get(String id,
          Handler<ExtendedAsyncResult<ModuleDescriptor>> fut) {
    logger.debug("get");
    pg.getConnection(gres -> {
      if (gres.failed()) {
        logger.fatal("get: getConnection() failed: " + gres.cause().getMessage());
        fut.handle(new Failure<>(gres.getType(), gres.cause()));
      } else {
        SQLConnection conn = gres.result();
        String sql = "SELECT " + jsonColumn + " FROM modules WHERE " + idSelect;
        JsonArray jsa = new JsonArray();
        jsa.add(id);
        conn.queryWithParams(sql, jsa, sres -> {
          if (sres.failed()) {
            logger.fatal("get failed: " + sres.cause().getMessage());
            pg.closeConnection(conn);
            fut.handle(new Failure<>(INTERNAL, sres.cause()));
          } else {
            ResultSet rs = sres.result();
            if (rs.getNumRows() == 0) {
              fut.handle(new Failure<>(NOT_FOUND, "Module " + id + " not found"));
            } else {
              JsonObject r = rs.getRows().get(0);
              String tj = r.getString(jsonColumn);
              ModuleDescriptor md = Json.decodeValue(tj, ModuleDescriptor.class);
              fut.handle(new Success<>(md));
            }
            pg.closeConnection(conn);
          }
        });
      }
    });
  }

  @Override
  public void getAll(Handler<ExtendedAsyncResult<List<ModuleDescriptor>>> fut) {
    logger.debug("getAll");
    pg.getConnection(gres -> {
      if (gres.failed()) {
        logger.fatal("getAll: getConnection() failed: "
                + gres.cause().getMessage());
        fut.handle(new Failure<>(gres.getType(), gres.cause()));
      } else {
        SQLConnection conn = gres.result();
        String sql = "SELECT " + jsonColumn + " FROM modules";
        conn.query(sql, sres -> {
          if (sres.failed()) {
            logger.fatal("getAll: select failed: "
                    + sres.cause().getMessage());
            fut.handle(new Failure<>(INTERNAL, sres.cause()));
          } else {
            ResultSet rs = sres.result();
            List<ModuleDescriptor> ml = new ArrayList<>();
            List<JsonObject> tempList = rs.getRows();
            for (JsonObject r : tempList) {
              String tj = r.getString(jsonColumn);
              ModuleDescriptor md = Json.decodeValue(tj, ModuleDescriptor.class);
              ml.add(md);
            }
            fut.handle(new Success<>(ml));
          }
          pg.closeConnection(conn);
        });
      }
    });
  }

  @Override
  public void delete(String id, Handler<ExtendedAsyncResult<Void>> fut) {
    logger.debug("delete");
    pg.getConnection(gres -> {
      if (gres.failed()) {
        logger.fatal("delete: getConnection() failed: "
                + gres.cause().getMessage());
        fut.handle(new Failure<>(gres.getType(), gres.cause()));
      } else {
        SQLConnection conn = gres.result();
        String sql = "DELETE FROM modules WHERE " + idSelect;
        JsonArray jsa = new JsonArray();
        jsa.add(id);
        conn.updateWithParams(sql, jsa, sres -> {
          if (sres.failed()) {
            logger.fatal("delete failed: " + sres.cause().getMessage());
            fut.handle(new Failure<>(INTERNAL, sres.cause()));
          } else {
            UpdateResult result = sres.result();
            if (result.getUpdated() > 0)
              fut.handle(new Success<>());
            else
              fut.handle(new Failure<>(NOT_FOUND, "Module " + id + " not found"));
          }
          pg.closeConnection(conn);
        });
      }
    });
  }
}
