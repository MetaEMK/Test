/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.qreator.vertx.VertxDatabase;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CookieHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.sstore.LocalSessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author menze
 */
public class HttpVerticle extends AbstractVerticle {

    private final int port = 8080;
    private static final Logger LOGGER = LoggerFactory.getLogger("de.qreator.vertx.VertxDatabase.HttpServer");
    private static final String EB_ADRESSE = "vertxdatabase.eventbus";


    public void start(Future<Void> startFuture) throws Exception {

        HttpServer server = vertx.createHttpServer();

        LocalSessionStore store = LocalSessionStore.create(vertx);
        SessionHandler sessionHandler = SessionHandler.create(store);

        Router router = Router.router(vertx);

        router.route().handler(CookieHandler.create());
        router.route().handler(sessionHandler);
        router.post().handler(BodyHandler.create());
        router.post("/anfrage").handler(this::anfragenHandler);
        router.route("/static/geheim/*").handler(this::geheimeSeiten);
        router.route("/static/*").handler(StaticHandler.create().setDefaultContentEncoding("UTF-8").setCachingEnabled(false));

        server.requestHandler(router::accept).listen(port, "0.0.0.0", listener -> {
            if (listener.succeeded()) {
                LOGGER.info("Http-Server auf Port " + port + " gestartet");
                startFuture.complete();
            } else {
                startFuture.fail(listener.cause());
            }
        });
    }

    private void geheimeSeiten(RoutingContext routingContext) {
        LOGGER.info("Router für geheime Seiten");
        Session session = routingContext.session();   
        if (session.get("angemeldet") == null) { // wenn nicht angemeldet, dann Passwort verlangen
            String name = session.get("name");
            LOGGER.info(name);
            routingContext.response().setStatusCode(303);
            routingContext.response().putHeader("Location", "/static/passwort.html");
            routingContext.response().end();
        } else {
            LOGGER.info("Weiterleitung zum nächsten Router");
            routingContext.next(); // sonst weiter zum nächsten Router
        }
    }

    private void anfragenHandler(RoutingContext routingContext) {
        
        LOGGER.info("Anfrage wird verarbeitet");
        String typ = routingContext.request().getParam("typ");
 
        HttpServerResponse response = routingContext.response();
        response.putHeader("content-type", "application/json");
        JsonObject jo = new JsonObject();
        Session session = routingContext.session();
        
        if (typ.equals("angemeldet")) { 
            LOGGER.info("Anfrage, ob User angemeldet ist.");
            String angemeldet = session.get("angemeldet");
           
            jo.put("typ", "angemeldet");
            String name = session.get("name");
           
            if (angemeldet != null && angemeldet.equals("ja")) {
                LOGGER.info("User ist angemeldet.");
       
               jo.put("text", "ja");
            } else {
                LOGGER.info("User ist NICHT angemeldet. Somit Passworteingabe erforderlich");
                jo.put("text", "nein");
            }
            response.end(Json.encodePrettily(jo));
            
        }
        
        else if (typ.equals("erstelleItem")){
            String user = session.get("name");
            
            String name = routingContext.request().getParam("Itemname");
            LOGGER.info(user + " erstellt das Shopitem: " + name);
            String preis = routingContext.request().getParam("Itempreis");
            JsonObject request = new JsonObject().put("name", name).put("preis", preis);
            DeliveryOptions opt = new DeliveryOptions().addHeader("action", "erstelleItem");
            vertx.eventBus().send(EB_ADRESSE, request, opt, reply -> {
                if (reply.succeeded()) {
                    LOGGER.info("test");
                   JsonObject body = (JsonObject) reply.result().body();
                   String result = body.getString("ersItem");
                    if (result.equals("ja")) {
                        jo.put("text", "Itemerstellt").put("itemers", "ja");
                        LOGGER.info("Shopitem: " + name +"  erstellt");
                    }
                    
                    else if (result.equals("existiert")){
                        jo.put("text", "Itemerstellt").put("itemers", "nein");
                        LOGGER.info("Shopitem existiert");
                    }
                    else {
                        jo.put("text", "Itemerstellt").put("itemers","fehler");
                        LOGGER.error("Fehler beim Erstellen eines Shopitems"+ reply.cause());
                        
                    }
                    response.end(Json.encodePrettily(jo));
                   
                }
                
 
            });
            
        }
                else if (typ.equals("setzeKonto")){
            String user = session.get("name");      
            String name = routingContext.request().getParam("Name");
            String Betrag = routingContext.request().getParam("Betrag");
            int konto = Integer.parseInt(Betrag);
            LOGGER.info(user + " verändert den Kontostand von: " + name + " auf "+ konto + "€");
            DeliveryOptions opt = new DeliveryOptions().addHeader("action", "uptKonto");
            
            JsonObject request = new JsonObject().put("name", name).put("konto", konto);
            vertx.eventBus().send(EB_ADRESSE,request, opt, reply ->{
                if (reply.succeeded()) {
                    jo.put("text", "updateKonto").put("setzeKonto", "success");
                    LOGGER.info("Kontostand update war erfolgreich");
            response.end(Json.encodePrettily(jo));
                }
                else{
                    JsonObject save = (JsonObject) reply.result().body();
                    String result = save.getString("uptKonto");
                    jo.put("text", "updateKonto").put("setzeKonto", result);
                    LOGGER.error("Fehler beim Setzen des Kontostandes" + reply.cause());
                    response.end(Json.encodePrettily(jo));
                }
            });
        }
        else if (typ.equals("anmeldedaten")) {
            String name = routingContext.request().getParam("anmeldename");
            String passwort = routingContext.request().getParam("passwort");
            LOGGER.info("Anmeldeanfrage von User " + name + " mit dem Passwort " + passwort);

            JsonObject request = new JsonObject().put("name", name).put("passwort", passwort);

            DeliveryOptions options = new DeliveryOptions().addHeader("action", "ueberpruefe-passwort");
            vertx.eventBus().send(EB_ADRESSE, request, options, reply -> {
                if (reply.succeeded()) {
                    JsonObject body = (JsonObject) reply.result().body();
                    if (body.getBoolean("passwortStimmt") == true) {
                        session.put("angemeldet", "ja").put("name", name);
                        LOGGER.info("der User " + name + " hat sich erfolgreich angemeldet");
                      
                        jo.put("typ", "überprüfung").put("text", "ok");
                        
                    } else {
                        LOGGER.info("für den Account: " + name + "wurde das falsche Passwort eingegeben");
                        jo.put("typ", "überprüfung").put("text", "nein");
                    }
                    response.end(Json.encodePrettily(jo));
                } else {
                    LOGGER.error("Bei der Anmeldung von: " + name + " ist ein Fehler aufgetreten:" + reply.cause());
                    jo.put("typ", "überprüfung").put("text", "nein");
                    response.end(Json.encodePrettily(jo));
                }
            });
            
        }
        else if (typ.equals("kaufeItem")){
            JsonObject alles = new JsonObject();
           
            String name = session.get("name");
            String item = routingContext.request().getParam("item");
            alles.put("name", name);
            LOGGER.info("Der User: " + name + " versucht das Item: " + item + " zu erwerben.");
         
            
            
              JsonObject requestPreis = new JsonObject().put("Gegenstand", item);
            DeliveryOptions Preisopt = new DeliveryOptions().addHeader("action", "getPreis");
             vertx.eventBus().send(EB_ADRESSE, requestPreis,Preisopt, reply ->{
                 if (reply.succeeded()) {
                     
                     JsonObject dbpreis = (JsonObject) reply.result().body();
                      int preis = dbpreis.getInteger("ItemPreis");
                      if (preis != -1) {
                         
                     
                      alles.put("Preis", preis);
                      LOGGER.info("das Item kostet: " + preis + "€");
                      
                      
                      
                      
                      
            JsonObject request = new JsonObject().put("name", name);
            DeliveryOptions opt = new DeliveryOptions().addHeader("action", "getKonto");
            vertx.eventBus().send(EB_ADRESSE, request,opt, replypreis ->{
                if (replypreis.succeeded()) {
                     JsonObject dbkonto = (JsonObject) replypreis.result().body();
                      int konto = dbkonto.getInteger("konto");
                      alles.put("konto", konto);
                      LOGGER.info("Der user hat einen Kontostand von: " + konto + "€");
             
                
          
           
             
           DeliveryOptions Itemopt = new DeliveryOptions().addHeader("action", "buyItem"); 
           LOGGER.info("Transaktion läuft");
           vertx.eventBus().send(EB_ADRESSE,alles,Itemopt,replyitem ->{
               
               JsonObject Ikauf = (JsonObject) replyitem.result().body();
               if (replyitem.succeeded()) {
                   
                   if (Ikauf.getString("Itemkauf").equals("erfolgreich")) {
                        jo.put("ItemKauf", "success");
                        response.end(Json.encodePrettily(jo));
                        LOGGER.info("Kauf erfolgreich");
                                
                   }
                  
               
               else if (Ikauf.getString("Itemkauf").equals("Kontostand")){
                   jo.put("ItemKauf", "Kontostand zu niedrig");
                   LOGGER.info("Der Kontostand von: "+ name + "ist zu niedrig");
                   response.end(Json.encodePrettily(jo));
               }              
               }
               

 
           });
       }
              
            });
                }else{
                     jo.put("ItemKauf", "Item existiert nicht");
                     LOGGER.info("das Item existiert nicht!");
                  response.end(Json.encodePrettily(jo));
                 }
                 }
                 
          
             });    
                 
                 
                 
                 
                 
                 
                 
                 
                 
                 
                 
                 
                 
                 
                 
                 }
                 
        else if (typ.equals("function")){
            String name = session.get("name");
            jo.put("name", name);
            LOGGER.info("Die Funktion von: " + name  + " wird überprüft");
            JsonObject request = new JsonObject().put("name", name);
            DeliveryOptions function2 = new DeliveryOptions().addHeader("action", "getFunction");
            vertx.eventBus().send(EB_ADRESSE, request,function2, reply ->{
                if (reply.succeeded()) {
                    JsonObject dbfunction = (JsonObject) reply.result().body();
                    String func = dbfunction.getString("function");
                    jo.put("function", func);
                    LOGGER.info("Der User hat die Funktion: " + func);
                       
                    
                }
            });
            JsonObject request2 = new JsonObject().put("name", name);
            DeliveryOptions konto2 = new DeliveryOptions().addHeader("action", "getKonto");
            vertx.eventBus().send(EB_ADRESSE, request2,konto2, reply ->{
                if (reply.succeeded()) {
                    JsonObject dbkonto = (JsonObject) reply.result().body();
                    int knt = dbkonto.getInteger("konto");
                    jo.put("konto", knt);
                     LOGGER.info("Der User hat einen Kontostand von: " + knt + "€");
                       
                    
                }
            });
                        JsonObject REadr = new JsonObject().put("name", name);
            DeliveryOptions adr2 = new DeliveryOptions().addHeader("action", "getAdresse");
            vertx.eventBus().send(EB_ADRESSE, REadr,adr2, reply ->{
                if (reply.succeeded()) {
                    JsonObject dbkonto = (JsonObject) reply.result().body();
                    String adre = dbkonto.getString("adresse");
                    jo.put("adresse", adre);
                       response.end(Json.encodePrettily(jo));
                        LOGGER.info("Der User hat die Adresse: " + adre);
                    
                }
            });
        }
        else if (typ.equals("löscheItem")){
           String user = session.get("name");
            String name = routingContext.request().getParam("Itemname");
             LOGGER.info(user+" löscht Item " + name);
            JsonObject request = new JsonObject().put("name", name);
            DeliveryOptions opt = new DeliveryOptions().addHeader("action", "löscheItem");
            vertx.eventBus().send(EB_ADRESSE, request, opt, reply ->{
                if (reply.succeeded()) {
                    jo.put("text", "ItemGelöscht").put("itemdelete", "ja");
                    response.end(Json.encodePrettily(jo));
                    LOGGER.info("löschen erfolgreich");
                }
                else{
                    jo.put("text", "ItemGelöscht").put("itemdelete", "fehler");
                    response.end(Json.encodePrettily(jo));
                    LOGGER.error("Beim Itemlöschen ist der Fehler: " + reply.cause() +" aufgetreten");
                }
 
            });
        }
        else if (typ.equals("AEadresse")){
          
            String name = session.get("name");
              LOGGER.info("Adresse von: "+ name +" wird geändert");
            String adresse = routingContext.request().getParam("Adresse");
            JsonObject request  = new JsonObject().put("name", name).put("adresse", adresse);
            DeliveryOptions options = new DeliveryOptions().addHeader("action", "changeAdresse");
            vertx.eventBus().send(EB_ADRESSE, request, options, reply -> {
                
                if (reply.succeeded()) {
                    JsonObject control = (JsonObject) reply.result().body();
                    LOGGER.info(control.getString("ÄnderrungAdresse"));
                    if (control.getString("ÄnderrungAdresse").equals("es tut")) {
                        jo.put("CHANGEadresse","erfolgreich");
                        LOGGER.info("Adresse erfolgreich geändert");
                        response.end(Json.encodePrettily(jo));
                    }
                    else{
                        jo.put("CHANGEadresse", "Adresse fehler");
                        response.end(Json.encodePrettily(jo));
                    }
                    
                }
            });
        }
        else if(typ.equals("Geld")){
           String user = session.get("name");
            String name = routingContext.request().getParam("Kontoname");
            JsonObject request = new JsonObject().put("name", name);
             LOGGER.info("Kontostand von " + name + " wird von " + user +" überprüft");
            DeliveryOptions options = new DeliveryOptions().addHeader("action", "getKonto");
            vertx.eventBus().send(EB_ADRESSE, request, options, reply -> {
                if (reply.succeeded()) {
                    JsonObject dbkonto = (JsonObject) reply.result().body();
                    int konto = dbkonto.getInteger("konto");
                    jo.put("konto", konto);
                     response.end(Json.encodePrettily(jo));
                }
                else{
                    jo.put("konto", "fehler"); 
                    LOGGER.error("" + reply.cause());
                response.end(Json.encodePrettily(jo));
                }
            });
                }
        else if(typ.equals("wechslePW")){
            String name=session.get("name");
            String pw = routingContext.request().getParam("pw");
            String passwort=routingContext.request().getParam("passwort");
            if (pw.isEmpty()||passwort.isEmpty()) {
              
                jo.put("text", "UpdatePasswort").put("PasswortUPT", "leer");
                response.end(Json.encodePrettily(jo));
            }
            else{
                
            JsonObject request2 = new JsonObject().put("name", name).put("passwort", pw);
            DeliveryOptions options2 = new DeliveryOptions().addHeader("action", "ueberpruefe-passwort");
            vertx.eventBus().send(EB_ADRESSE, request2, options2, reply2 -> {
                if(reply2.succeeded()){
                     JsonObject result = (JsonObject) reply2.result().body();
                     if (result.getBoolean("passwortStimmt") == true) {
                  
                    
    
            JsonObject request = new JsonObject().put("name", name).put("passwort", passwort);
            DeliveryOptions options = new DeliveryOptions().addHeader("action", "updPW");
             vertx.eventBus().send(EB_ADRESSE, request, options, reply -> {
              
                 if (reply.succeeded()) {
                     JsonObject res = (JsonObject) reply.result().body();
                     if (res.getString("updatePW").equals("success")) {
                         LOGGER.info("success");
                         jo.put("text", "UpdatePasswort").put("PasswortUPT", "success");
                         response.end(Json.encodePrettily(jo));
                     }
                     else {
                         LOGGER.error("Passwortänderrung fehlgeschlagen");
                         jo.put("text", "UpdatePasswort").put("PasswortUPT", "error");
                         response.end(Json.encodePrettily(jo));
                     }
                 }
                 else{
                     LOGGER.error("" + reply.cause());
                 }
             });
              }
                     else{
                         jo.put("text", "UpdatePasswort").put("PasswortUPT", "passwort");
                           response.end(Json.encodePrettily(jo));
                                 
                     }
                }
                
            });
        
            }}
        else if (typ.equals("registrierung")) {
            LOGGER.info("daten erhalten");
            String name=routingContext.request().getParam("regname");
            String passwort=routingContext.request().getParam("passwort");
            String adresse = routingContext.request().getParam("regadresse");
            if (!name.isEmpty() && !passwort.isEmpty() && !adresse.isEmpty()) {
                LOGGER.info("Registration");
           
            JsonObject request = new JsonObject().put("name", name).put("passwort", passwort).put("adresse", adresse);
            DeliveryOptions options = new DeliveryOptions().addHeader("action", "erstelleUser");
            vertx.eventBus().send(EB_ADRESSE, request, options, reply -> {
              
                if (reply.succeeded()) {
                        LOGGER.info("Reg: Datenübermittlung erfolgt");       
                    JsonObject test = (JsonObject) reply.result().body();
                
                    if (test.getBoolean("REGsuccess")== true) {
                        jo.put("typ", "bestätigung").put("text", "richtig");
                    }
                    else{
                        LOGGER.info("user exists");
                        jo.put("typ", "bestätigung").put("text", "falsch");
                    }
                     response.end(Json.encodePrettily(jo));
                    LOGGER.info("Reg: Datenübermittlung fertig");
                }
                else{
                    LOGGER.error("REG: Datenbankantwort FEHLER");
                }
            });
        }
            else{
                LOGGER.info("leer");
                jo.put("typ", "bestätigung").put("text", "leer");
                response.end(Json.encodePrettily(jo));
            }}
        else if (typ.equals("zeigeItem")){
            JsonObject request = new JsonObject();
             DeliveryOptions options = new DeliveryOptions().addHeader("action", "zeigeItems");
             DeliveryOptions opt = new DeliveryOptions().addHeader("action", "getPreis");
             vertx.eventBus().send(EB_ADRESSE, request,options,  reply -> {
                if( reply.succeeded()){
                    JsonObject ant = (JsonObject) reply.result().body();
                    jo.put("size", (ant.size()-1)/2);
                    LOGGER.info("" + jo.getInteger("size"));
                    for (int i = 0; i < ant.getInteger("size"); i++) {
                      
                        String a = ant.getString("test " + i);
                        int preis = ant.getInteger("preis2" + i);
                        jo.put("text", "Itemnamen").put("items"+i, a).put("preis" + i, preis);
                        
                        }
                    response.end(Json.encodePrettily(jo));
                        
                    }
                });
             
                    
                       
                    
             
             
        }
        else if (typ.equals("deleteUser")) {
            LOGGER.info("lösche User");
            String name = routingContext.request().getParam("username");
            DeliveryOptions options = new DeliveryOptions().addHeader("action", "deleteUser");
            JsonObject request = new JsonObject().put("name", name);
            vertx.eventBus().send(EB_ADRESSE,request,options,reply -> {
              
                if (reply.succeeded()) {
                    JsonObject ant = (JsonObject) reply.result().body();
                    String antwort = ant.getString("Userdelete");
                      
                    if (antwort.equals("ja")) {
                          LOGGER.info("User geläscht");
                       jo.put("delUser", "success");
                    }
                    else if(antwort.equals("not found")){
                         
                        jo.put("delUser", "where");
                    }
                    else{
                          
                        jo.put("delUser", "fehler");
                    }
                    response.end(Json.encodePrettily(jo));
                }
                
 
            });
        }
        else if (typ.equals("zeigeUseran")) {
            LOGGER.info("zeige alle User an");
           
                        JsonObject request = new JsonObject();
             DeliveryOptions options = new DeliveryOptions().addHeader("action", "zeigeUser");
            
             vertx.eventBus().send(EB_ADRESSE, request,options,  reply -> {
                if( reply.succeeded()){
                    JsonObject ant = (JsonObject) reply.result().body();
                    jo.put("size2", (ant.size()+1)/6);
                    
                    LOGGER.info("datenbank info erhalten");
                    for (int i = 0; i < ant.getInteger("size"); i++) {
                        int id = ant.getInteger("id" + i);
                      
                        String name = ant.getString("name" + i);


                        String passwort = ant.getString("passwort" + i);
                    
                        String adresse = ant.getString("adresse" + i);
                   
                        int money = ant.getInteger("money" + i);
                       
                        String function = ant.getString("function" + i);
                         
                            jo.put("text","zeigeALLEuser" ).put("id", id).put("name"+i, name).put("adresse" + i, adresse).put("money" +i, money).put("function" + i, function).put("passwort" +i, passwort);  
                      
                        }
                    response.end(Json.encodePrettily(jo));
                   
                        
                    }
                });
        }
        else if (typ.equals("Shopoffnen")){
            LOGGER.info("shop wird aufgerufen");
            String username = session.get("name");
            
            String Gegenstand1 = routingContext.request().getParam("search1"); 
            
            
            JsonObject request = new JsonObject().put("name", username);
            DeliveryOptions options = new DeliveryOptions().addHeader("action", "getKonto");
            LOGGER.info("Suchanfrage angekommen");
            vertx.eventBus().send(EB_ADRESSE, request ,options,  reply -> {
                if (reply.succeeded()) {
                    JsonObject dbkonto = (JsonObject) reply.result().body();
                    int konto = dbkonto.getInteger("konto");
                    LOGGER.info("tut");
                    jo.put("Kontostand", konto);
                    
                    
                }
                
            });
            JsonObject Gegenstand = new JsonObject().put("Gegenstand", Gegenstand1);
            DeliveryOptions opt = new DeliveryOptions().addHeader("action", "getPreis");
            vertx.eventBus().send(EB_ADRESSE, Gegenstand, opt, abfrage -> {
                if (abfrage.succeeded()) {
                
                  JsonObject pr  =  (JsonObject) abfrage.result().body();
                
                    if (pr.getInteger("ItemPreis")== -1) {
                        LOGGER.info("ad");
                        jo.put("ItemPreis", "nonexistent");
                        response.end(Json.encodePrettily(jo));
                       
                    }
                    else{
                        LOGGER.info("adsfkj");
                        
                        int a = pr.getInteger("ItemPreis");
                        jo.put("ItemPreis123", a);
                        
                        response.end(Json.encodePrettily(jo));
                    }
                }
            });
        }
        
        
        else if (typ.equals("logout")) {
            LOGGER.info("Logout-Anfrage");
            session.put("angemeldet", null).put("name", null);
            
            jo.put("typ", "logout");
            response.end(Json.encodePrettily(jo));
            jo.put("typ", "überprüfung").put("text", "ok");
        }
    
    }

 
}
