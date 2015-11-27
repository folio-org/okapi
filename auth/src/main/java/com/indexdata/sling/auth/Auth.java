/*
 * Copyright (c) 2015-2015, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package com.indexdata.sling.auth;

import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A dummy auth module.
 * Provides a minimal authentication mechanism.  
 * @author heikki
 */

public class Auth {

    static final String SLINGTOKENHEADER = "X-Sling-Token";
    
  /**
   * Calculate a token from tenant and username.
   * The token is like ttt:uuu:ccc, where ttt is the tenant, uuu is the user,
   * and ccc is a crypto thing that depends on those two. 
   * @param tenant
   * @param user
   * @return
   * @throws NoSuchAlgorithmException which should never happen
   */
  private String token (String tenant, String user ) throws NoSuchAlgorithmException {
    MessageDigest md = MessageDigest.getInstance("MD5");
    String salt = "salt"; // TODO - Add something from the current date
    md.update(salt.getBytes());
    md.update(tenant.getBytes());
    md.update(user.getBytes());
    byte[] mdbytes = md.digest();     

    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < mdbytes.length; i++) {
      sb.append(Integer.toString((mdbytes[i] & 0xff) + 0x100, 16).substring(1));
    }
    return "" + tenant + ":" + user + ":" + sb.toString();
  };

  public void login(RoutingContext ctx) {
    //System.out.println("Auth.login called");
    final String json = ctx.getBodyAsString();
    LoginParameters p;
    try {
      //System.out.println("Got some json" + json);
      p = Json.decodeValue(json, LoginParameters.class);
      //System.out.println("Parsed the params");
    } catch (DecodeException ex) {
      ctx.response()
        .setStatusCode(400)  // Bad request
        .end("Error in decoding parameters: " + ex); // Check symbolic name for "forbidden"
      return;      
    }
    
    // Simple password validation: "peter" has a password "peter36", etc
    String u = p.getUsername();
    String correctpw = u + "36";
    if ( ! p.getPassword().equals(correctpw)) {
      System.out.println("Bad passwd for '" + u + "'. "
        + "Got '" + p.getPassword() + "' expected '" + correctpw + "'" );
      ctx.response()
        .setStatusCode(401)  // Forbidden
        .end("Wrong username or password"); 
      return;
      
    }
    String tok;
    try {
      tok = token(p.getTenant(), p.getUsername());
      //System.out.println("Got a token " + tok);
    } catch (NoSuchAlgorithmException ex) {
      ctx.response()
        .setStatusCode(500)  // Internal error
        .end("Error in invoking MD5sum: "+ex); 
      return;
    }
    System.out.println("Ok login for " + u + ": " + tok);
    ctx.response()
      .headers().add(SLINGTOKENHEADER,tok);
    ctx.response()      
      .setStatusCode(200).end(tok); // OK
    
    
  }

  public void check (RoutingContext ctx) {
    String tok = ctx.request().getHeader(SLINGTOKENHEADER);
    if ( tok == null || tok.isEmpty() ) {
      System.out.println("Auth.check called without " + SLINGTOKENHEADER);
      ctx.response()
        .setStatusCode(401) // Check symbolic name for "forbidden"
        .end("Auth.check called without " + SLINGTOKENHEADER ); 
      return;
    }
    //System.out.println("Auth.check called with " + tok);
      // Do some magic
    String[] split = tok.split(":",3);
    String properToken = "???";
    try {
      if ( split.length == 3 )
        properToken = token(split[0],split[1]);
      if ( ! tok.equals(properToken)) {
        System.out.println("Invalid token. "
          + "Got '" + tok + "' Expected '" + properToken + "'");
        ctx.response()
          .setStatusCode(401) // Check symbolic name for "forbidden"
          .end("Invalid token"); 
        return;          
        }
      } catch (NoSuchAlgorithmException ex) {
        Logger.getLogger(Auth.class.getName()).log(Level.SEVERE, null, ex);
      }
    System.out.println("Auth check OK");
    ctx.response()
      .headers().add(SLINGTOKENHEADER,tok);
    ctx.response().setStatusCode(202).end(); // 202 = Accepted
    // signal to the conduit that we want to continue the module chain
  }
  
  /** 
   * Accept a request.
   * Gets called with anything else than a POST to /login. These need to be
   * accepted, so we can do a pre-check before the proper POST
   * @param ctx 
   */
  public void accept (RoutingContext ctx) {
    System.out.println("Auth accept OK");
    ctx.response().setStatusCode(202).end("Accepted"); 
    
  }

  
}
