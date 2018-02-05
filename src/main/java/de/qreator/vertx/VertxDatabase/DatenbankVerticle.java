package de.qreator.vertx.VertxDatabase;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.sql.SQLRowStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javafx.scene.chart.XYChart.Series;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatenbankVerticle extends AbstractVerticle {

    private static final String SQL_NEUE_TABELLE_SHOP = "create table if not exists item(id int auto_increment, name varchar(20) not null, preis int not null,primary key(name))";
    private static final String SQL_NEUE_TABELLE = "create table if not exists user(id int auto_increment,name varchar(20) not null, passwort varchar(9999) not null,adresse varchar(20) not null,money int not null,function varchar(20) not null,primary key(name))";
    private static final String SQL_ÜBERPRÜFE_PASSWORT = "select passwort from user where name=?";
    private static final String SQL_ÜBERPRÜFE_EXISTENZ_USER = "select name from user where name=?";
    private static final String SQL_ÜBERPRÜFE_FUNCTION = "select function from user where name =?";
    private static final String SQL_DELETE_ITEM = "delete from item where name =?";
    private static final String SQL_DELETE_USER = "delete from user where name =?";
    private static final String SQL_ÜBERPRÜFE_KONTO = "select money from user where name =?";
    private static final String SQL_ÜBERPRÜFE_ADRESSE = "select adresse from user where name =?";
    private static final String SQL_ÜBERPRÜFE_ITEM = "select name from item where name =?";

    private static final String SQL_DELETE = "drop table user";
    private static final String SQL_ZEIGE_ITEMS = "select name,preis from item";
    private static final String SQL_ÜBERPRÜFE_PREIS = "select preis from item where name =?";
    private static final String USER_EXISTIERT = "USER_EXISITIERT";
    private static final String SQL_ÜBERPRÜFE_ITEMNAME = "select name from item where name =?";
    private static final String EB_ADRESSE = "vertxdatabase.eventbus";

    private enum ErrorCodes {
        KEINE_AKTION,
        SCHLECHTE_AKTION,
        DATENBANK_FEHLER
    }

    // Logger erzeugen, wobei gilt: TRACE < DEBUG < INFO <  WARN < ERROR
    private static final Logger LOGGER = LoggerFactory.getLogger("de.qreator.vertx.VertxDatabase.Datenbank");

    private JDBCClient dbClient;

    public void start(Future<Void> startFuture) throws Exception {
        
        JsonObject config = new JsonObject()
                .put("url", "jdbc:h2:~/datenbank3")
                .put("driver_class", "org.h2.Driver");

        dbClient = JDBCClient.createShared(vertx, config);

        Future<Void> datenbankFuture = erstelleDatenbank(); //.compose(db -> erstelleUser("user", "geheim"));

        erstelleShopDB();

        datenbankFuture.setHandler(db -> {
            if (db.succeeded()) {
                String pw32 = "passwort";
                pw32 = hashcode(pw32);
                LOGGER.info("Datenbank initialisiert");
                vertx.eventBus().consumer(EB_ADRESSE, this::onMessage);
                erstelleUser("Jan Benecke", pw32, "unknown", "admin", Integer.MAX_VALUE);
                erstelleUser("Jan Maly", pw32, "unknown", "admin", Integer.MAX_VALUE);

                startFuture.complete();
            } else {
                LOGGER.info("Probleme beim Initialisieren der Datenbank");
                startFuture.fail(db.cause());
            }
        });

    }

    public void onMessage(Message<JsonObject> message) {

        if (!message.headers().contains("action")) {
            LOGGER.error("Kein action-Header übergeben!",
                    message.headers(), message.body().encodePrettily());
            message.fail(ErrorCodes.KEINE_AKTION.ordinal(), "Keine Aktion im Header übergeben");
            return;
        }
        String action = message.headers().get("action");

        switch (action) {
            case "updPW":
                uptPasswort(message);
                break;
            case "deleteUser":
                deleteUser(message);
                break;
            case "zeigeUser":
                zeigeUser(message);
                break;
            case "buyItem":
                buyItem(message);
                break;
            case "getPreis":
                getPreis(message);
                break;
            case "Shopausgeben":
                Shopausgeben(message);
                break;
            case "uptKonto":
                uptKonto(message);
                break;
            case "zeigeItems":
                test(message);
                break;
            case "löscheItem":
                löscheItem(message);
                break;
            case "erstelleItem":
                erstelleItem(message);
                break;
            case "ueberpruefe-passwort":
                überprüfeUser(message);
                break;
            case "erstelleUser":
                erstelleNeuenUser(message);
                break;
            case "getFunction":
                getFunction(message);
                break;
            case "getKonto":
                getKonto(message);
                break;
            case "getAdresse":
                getAdresse(message);
                break;
            case "changeAdresse":
                uptAdresse(message);
                break;
            default:
                message.fail(ErrorCodes.SCHLECHTE_AKTION.ordinal(), "Schlechte Aktion: " + action);
        }
    }
private String hashcode(String res){
LOGGER.info(res + " das ist ein Klartext");
    InputStream stream = new ByteArrayInputStream(res
        .getBytes(StandardCharsets.UTF_8));
// oder, um einen Hash für eine Datei zu bestimmen:
// InputStream stream = new FileInputStream("D:\\test.png");
try {
    // SHA-1, MD5 oder SHA-256
    //System.out.println(Hash.checksum(stream, "SHA-256"));
    res = Hash.checksum(stream, "SHA-256");
} catch (Exception e) {
    e.printStackTrace();
}
   return res; 
}
    private void löscheDatenbank() {

        dbClient.getConnection(res -> {
            if (res.succeeded()) {
                SQLConnection connection = res.result();
                connection.execute(SQL_DELETE, löschen -> {
                    if (löschen.succeeded()) {
                        LOGGER.info("Datenbank erfolgreich gelöscht");

                    } else {
                        LOGGER.error("Löschen der Datenbank fehlgeschlagen " + löschen.cause());

                    }
                });
            }

        });
    }

    private void uptKonto(Message<JsonObject> message) {
        String name = message.body().getString("name");
        int konto = message.body().getInteger("konto");
     
        LOGGER.info(name + "");
        dbClient.getConnection(res -> {
            if (res.succeeded()) {
                SQLConnection connection = res.result();
                connection.queryWithParams(SQL_ÜBERPRÜFE_EXISTENZ_USER, new JsonArray().add(name), result->{
                    if (result.succeeded()) {
                        List<JsonArray> liste = result.result().getResults();
                        if (!liste.isEmpty()) {
                    
                connection.execute("update user set money = " + konto + " where name = " + "'" + name + "'" + "", abfrage -> {
                    if (abfrage.succeeded()) {
                        message.reply(new JsonObject().put("uptKonto", "success"));

                    } else {
                        LOGGER.error("" + abfrage.cause());
                        message.reply(new JsonObject().put("uptKonto", abfrage.cause().toString()));
                    }

                });
            
 
        }
                   
                };
                        });
            };
    });
                }

    private void getPreis(Message<JsonObject> message) {
        String itemname = message.body().getString("Gegenstand");
        LOGGER.info(itemname);
        dbClient.getConnection(res -> {
            if (res.succeeded()) {
                SQLConnection connection = res.result();
                connection.queryWithParams(SQL_ÜBERPRÜFE_ITEM, new JsonArray().add(itemname), abfrage -> {
                    if (abfrage.succeeded()) {

                        List<JsonArray> liste = abfrage.result().getResults();

                        if (liste.isEmpty()) {

                            message.reply(new JsonObject().put("ItemPreis", -1));
                        } else {

                            connection.queryWithParams(SQL_ÜBERPRÜFE_PREIS, new JsonArray().add(itemname), ab -> {

                                if (ab.succeeded()) {
                                    List<JsonArray> liste2 = ab.result().getResults();

                                    int zeilen = liste2.get(0).getInteger(0);

                                    message.reply(new JsonObject().put("ItemPreis", zeilen));
                                }
                            });
                        }
                    }
                });

            }

        });
    }

    private Future<Void> erstelleDatenbank() {

        Future<Void> erstellenFuture = Future.future();
        LOGGER.info("Datenbank neu anlegen, falls nicht vorhanden.");
        dbClient.getConnection(res -> {
            if (res.succeeded()) {

                SQLConnection connection = res.result();

                connection.execute(SQL_NEUE_TABELLE, erstellen -> {
                    if (erstellen.succeeded()) {
                        erstellenFuture.complete();
                    } else {
                        LOGGER.error(erstellen.cause().toString());
                        erstellenFuture.fail(erstellen.cause());
                    }
                });
            } else {
                LOGGER.error("Problem bei der Verbindung zur Datenbank");
            }
        });
        return erstellenFuture;
    }

    private void zeigeUser(Message<JsonObject> message) {
        LOGGER.info("Datenbank checkt alle User");
        dbClient.getConnection(res -> {
            if (res.succeeded()) {
                SQLConnection con = res.result();
                con.query("select id,name,passwort,adresse,money,function from user", reply -> {
                    JsonObject adf = new JsonObject();
                    LOGGER.info("erfolgreich");
                    if (reply.succeeded()) {
                        LOGGER.info("Übermittlung fängt an");
                        List<JsonArray> liste = reply.result().getResults();
                        for (int i = 0; i < liste.size(); i++) {
                            JsonArray array = liste.get(i);
                            adf.put("id" + i, array.getInteger(0));
                            adf.put("name" + i, array.getString(1));
                            adf.put("passwort" + i, array.getString(2));
                            adf.put("adresse" + i, array.getString(3));
                            adf.put("money" + i, array.getInteger(4));
                            adf.put("function" + i, array.getString(5));

                        }
                        adf.put("size", liste.size());
                        message.reply(adf);
                        LOGGER.info("Übermittlung fertig");
                    }
                });
            }
        });
    }

    private void deleteUser(Message<JsonObject> m) {
        LOGGER.info("DB löscht User");
        String name = m.body().getString("name");
        dbClient.getConnection(res -> {
            if (res.succeeded()) {
                SQLConnection con = res.result();
                con.queryWithParams(SQL_ÜBERPRÜFE_EXISTENZ_USER, new JsonArray().add(name), ant -> {
                    if (ant.succeeded()) {
                        List<JsonArray> liste = ant.result().getResults();
                        if (!liste.isEmpty()) {

                            con.querySingleWithParams(SQL_DELETE_USER, new JsonArray().add(name), reply -> {
                                if (reply.succeeded()) {
                                    m.reply(new JsonObject().put("Userdelete", "ja"));
                                    LOGGER.info("antwort wird formuliert");
                                } else {
                                    m.reply(new JsonObject().put("Userdelete", "fehler"));
                                    LOGGER.error(reply.cause().toString());
                                }

                            });
                        } else {
                            m.reply(new JsonObject().put("Userdelete", "not found"));
                        }
                    }
                });
            }
        });
    }

    private void erstelleNeuenUser(Message<JsonObject> message) {
        String name = message.body().getString("name");
    
        String passwort2 = message.body().getString("passwort");
        passwort2 = hashcode(passwort2);
       
        String adresse = message.body().getString("adresse");
        String function = message.body().getString("function");
        if (function == null) {
            function = "user";
        }
        int money = 0;
        Future<Void> userErstelltFuture = erstelleUser(name, passwort2, adresse, function, money);
        userErstelltFuture.setHandler(reply -> {
            if (reply.succeeded()) {
                LOGGER.info("REG: reply (positive) sent");
                message.reply(new JsonObject().put("REGsuccess", Boolean.TRUE));
            } else {
                String grund = reply.cause().toString();
                LOGGER.info(grund);
                if (grund.equals("io.vertx.core.impl.NoStackTraceThrowable: USER_EXISITIERT")) {
                    LOGGER.info("REG: reply (negative) sent");
                    message.reply(new JsonObject().put("REGsuccess", Boolean.FALSE));
                }
            }

        });
    }

    private void erstelleShopDB() {

        LOGGER.info("Shop Datenbank wird erstellt");

        dbClient.getConnection(res -> {
            if (res.succeeded()) {
                SQLConnection connection = res.result();
                connection.execute(SQL_NEUE_TABELLE_SHOP, erstellen -> {

                    if (erstellen.succeeded()) {

                        LOGGER.info("Shop Datenbank erfolgreich erstellt!");
                    } else {

                        LOGGER.error(erstellen.cause().toString());

                    }
                });
            }
        });
    }

    private void Shopausgeben(Message<JsonObject> message) {
        String name = message.body().getString("search");
        dbClient.getConnection(res -> {
            if (res.succeeded()) {
                SQLConnection connection = res.result();
                connection.queryWithParams(SQL_ÜBERPRÜFE_ITEM, new JsonArray().add(name), abfrage -> {
                    if (abfrage.succeeded()) {
                        LOGGER.info("In der Datenbank sind Einträge");
                        List<JsonArray> liste = abfrage.result().getResults();

                        if (liste.isEmpty()) {
                            LOGGER.info("ein solches Item existiert nicht");
                            message.reply(new JsonObject().put("ItemExistenz", Boolean.FALSE));
                        }
                    } else {
                        LOGGER.info("In der Datenbank sind Einträge");
                        message.reply(new JsonObject().put("ItemExistenz", Boolean.TRUE));
                    }
                });
            }
        });
    }

    private void löscheItem(Message<JsonObject> message) {
        String name = message.body().getString("name");
        dbClient.getConnection(res -> {
            if (res.succeeded()) {
                SQLConnection connection = res.result();
                connection.queryWithParams(SQL_DELETE_ITEM, new JsonArray().add(name), lösche -> {
                    if (lösche.succeeded()) {
                        message.reply(new JsonObject().put("deleteItem", "success"));
                    }
                });
            }
        });
    }

    private void test(Message<JsonObject> message) {
        dbClient.getConnection(res -> {
            if (res.succeeded()) {
                SQLConnection con = res.result();
                con.query(SQL_ZEIGE_ITEMS, baum -> {
                    LOGGER.info("tut");
                    if (baum.succeeded()) {
                        JsonObject adf = new JsonObject();
                        LOGGER.info("tut2");
                        List<JsonArray> liste = baum.result().getResults();
                        for (int i = 0; i < liste.size(); i++) {
                            JsonArray test = liste.get(i);
                            adf.put("test " + i, test.getString(0));
                            adf.put("preis2" + i, test.getInteger(1));
                        }
                        int ads = liste.size();

                        adf.put("size", ads);
                        message.reply(adf);

                    } else {
                        LOGGER.error("Fehler" + baum.cause());
                    }
                });
            }
        });
    }

    private void erstelleItem(Message<JsonObject> message) {

        String name = message.body().getString("name");
        String preis2 = message.body().getString("preis");
        int preis = Integer.parseInt(preis2);
        dbClient.getConnection(res -> {
            if (res.succeeded()) {
                SQLConnection connection = res.result();

                connection.queryWithParams(SQL_ÜBERPRÜFE_ITEMNAME, new JsonArray().add(name), abfrage -> {

                    if (abfrage.succeeded()) {

                        List<JsonArray> liste = abfrage.result().getResults();
                        if (liste.isEmpty()) {

                            connection.execute("insert into item(name,preis) values ('" + name + "','" + preis + "')", erstellen -> {
                                if (erstellen.succeeded()) {

                                    message.reply(new JsonObject().put("ersItem", "ja"));
                                } else {
                                    message.reply(new JsonObject().put("ersItem", "fehler"));
                                    LOGGER.error("Fehler beim Einfügen eines Shopitems: " + erstellen.cause());
                                }
                            });
                        } else {
                            LOGGER.info("Shopitem existiert schon");
                            message.reply(new JsonObject().put("ersItem", "existiert"));

                        }
                    } else {
                        LOGGER.error("" + abfrage.cause());
                    }

                });
            } else {
                LOGGER.error("Verbindung fehlgeschlagen: " + res.cause());
            }
        });
    }

    private void getKonto(Message<JsonObject> message) {
        String name = message.body().getString("name");
        dbClient.getConnection(res -> {
            if (res.succeeded()) {
                SQLConnection connection = res.result();
                connection.queryWithParams(SQL_ÜBERPRÜFE_KONTO, new JsonArray().add(name), abfrage -> {
                    if (abfrage.succeeded()) {
                        List<JsonArray> liste = abfrage.result().getResults();
                        if (liste.isEmpty()) {
                            message.reply(new JsonObject().put("konto", "leer"));
                        }else{
                        int zeilen = liste.get(0).getInteger(0);

                        message.reply(new JsonObject().put("konto", zeilen));

                        LOGGER.info("KONTO: Der Kontostand von " + name + " beträgt " + zeilen);
                        }
                    }

                });
            } else {
                LOGGER.error("KONTO: Fehler bei der Verbindung mit der Datenbank: " + res.cause());
            }
        });
    }

    private void getAdresse(Message<JsonObject> message) {
        String name = message.body().getString("name");
        dbClient.getConnection(res -> {
            if (res.succeeded()) {
                SQLConnection connection = res.result();
                connection.queryWithParams(SQL_ÜBERPRÜFE_ADRESSE, new JsonArray().add(name), abfrage -> {
                    if (abfrage.succeeded()) {
                        List<JsonArray> liste = abfrage.result().getResults();
                        String zeilen = liste.get(0).toString();

                        message.reply(new JsonObject().put("adresse", zeilen));
                        LOGGER.info("ADRESSE: Die Adresse von " + name + " ist " + zeilen);
                    }

                });
            } else {
                LOGGER.error("ADRESSE: Fehler bei der Verbindung mit der Datenbank: " + res.cause());
            }
        });
    }

    private void buyItem(Message<JsonObject> message) {
        LOGGER.info("Datenbank nimmt daten auf");
        String name = message.body().getString("name");
        int konto = message.body().getInteger("konto");
        int preis = message.body().getInteger("Preis");
        int Kontostand = konto - preis;
        if (Kontostand >= 0) {
            LOGGER.info("Kontostand: " + Kontostand);

            dbClient.getConnection(res -> {
                if (res.succeeded()) {
                    SQLConnection connection = res.result();
                    connection.execute("update user set money = " + "'" + Kontostand + "'" + " where name = " + "'" + name + "'" + "", reply -> {
                        if (reply.succeeded()) {
                            message.reply(new JsonObject().put("Itemkauf", "erfolgreich"));
                            LOGGER.info("Kauf erfolgreich");
                        } else {
                            message.reply(new JsonObject().put("Itemkauf", "Fehler"));
                            LOGGER.info("Kauf nicht erfolgreich");
                        }
                    });
                }
            });
        } else {
            message.reply(new JsonObject().put("Itemkauf", "Kontostand"));
            LOGGER.info("Kauf nicht erfolgreich Kontostand zu niedrig");
        }
    }

    private void getFunction(Message<JsonObject> message) {
        String name = message.body().getString("name");
        dbClient.getConnection(res -> {
            if (res.succeeded()) {
                SQLConnection connection = res.result();
                connection.querySingleWithParams(SQL_ÜBERPRÜFE_FUNCTION, new JsonArray().add(name), abfrage -> {

                    if (abfrage.succeeded()) {
                        String zeilen = abfrage.result().toString();
                        if (zeilen.isEmpty()) {
                            LOGGER.error("FUNC: Diesen User gibt es nicht");
                        } else {
                            String function = zeilen;//.get(0).toString();
                            message.reply(new JsonObject().put("function", function));
                        }
                    } else {
                        LOGGER.error("FUNC: Antwortfehler");
                    }

                });

            }

        });
    }

    private Future<Void> erstelleUser(String name, String passwort, String adresse, String function, Integer money) {

        Future<Void> erstellenFuture = Future.future();

        dbClient.getConnection(res -> {
            if (res.succeeded()) {

                SQLConnection connection = res.result();

                connection.queryWithParams(SQL_ÜBERPRÜFE_EXISTENZ_USER, new JsonArray().add(name), abfrage -> {
                    if (abfrage.succeeded()) {
                        LOGGER.error("jetzt kommt der 1. fehler");
                        List<JsonArray> zeilen = abfrage.result().getResults();
                        if (zeilen.isEmpty()) { // User existiert noch nicht
                            LOGGER.info("Erstelle einen User mit dem Namen " + name + " und dem Passwort " + passwort);
                            connection.execute("insert into user(name,passwort,adresse,money,function) values('" + name + "','" + passwort + "','" + adresse + "','" + money + "','" + function + "')", erstellen -> {
                                if (erstellen.succeeded()) {
                                    LOGGER.info("User " + name + " erfolgreich erstellt");
                                    erstellenFuture.complete();

                                } else {
                                    LOGGER.info(erstellen.cause().toString());
                                    erstellenFuture.fail(erstellen.cause());
                                }
                            });
                        } else {
                            LOGGER.info("User mit dem Namen " + name + " existiert bereits.");
                            //erstellenFuture.fail("User existiert bereits!"); 
                            erstellenFuture.fail(USER_EXISTIERT);

                        }
                    } else {
                        erstellenFuture.fail(abfrage.cause());
                    }

                });
            } else {
                LOGGER.error("Problem bei der Verbindung zur Datenbank");
                erstellenFuture.fail(res.cause());
            }
        });
        return erstellenFuture;
    }

    private void überprüfeUser(Message<JsonObject> message) {

        String name = message.body().getString("name");
        String passwort2 = message.body().getString("passwort");
        LOGGER.info("Überprüfe, ob der Nutzer " + name + " mit dem Passwort " + passwort2 + " sich anmelden kann."); 
        String pw = null;
        pw = hashcode(passwort2);
        final String passwort = pw;
        pw= null;
        LOGGER.info("Überprüfe, ob der Nutzer " + name + " mit dem Passwort " + passwort + " sich anmelden kann."); 

        

        dbClient.queryWithParams(SQL_ÜBERPRÜFE_PASSWORT, new JsonArray().add(name), abfrage -> {
            if (abfrage.succeeded()) {
                List<JsonArray> zeilen = abfrage.result().getResults();
                if (zeilen.size() == 1) {
                    String passwortDB = zeilen.get(0).getString(0);
LOGGER.info("Hash: " + passwort + "DB: " + passwortDB);
                    if (passwortDB.equals(passwort)) {
                        
                        message.reply(new JsonObject().put("passwortStimmt", Boolean.TRUE));
                        LOGGER.info("Anmeldename und Passwort stimmen überein");
                    } else {
                        message.reply(new JsonObject().put("passwortStimmt", Boolean.FALSE));
                    }
                } else {
                    LOGGER.info("Anmeldename und Passwort stimmen NICHT überein");
                    message.reply(new JsonObject().put("passwortStimmt", Boolean.FALSE));
                }
            } else {
                message.reply(new JsonObject().put("passwortStimmt", Boolean.FALSE));
            }
        });
    }

    private void uptPasswort(Message<JsonObject> message) {
        LOGGER.info("passwort wird geändert");
        String name = message.body().getString("name");
        String pw1 = message.body().getString("passwort");
        final String pw = hashcode(pw1);
        
        dbClient.getConnection(res -> {
            if (res.succeeded()) {
                SQLConnection connection = res.result();
                connection.query("update user set passwort = " + "'" + pw + "'" + " where name = " + "'" + name + "'" + "", change -> {
                    if (change.succeeded()) {
                        message.reply(new JsonObject().put("updatePW", "success"));
                        LOGGER.error("Passwort ändern: success");

                    } else {
                        message.reply(new JsonObject().put("updatePW", "error"));
                        LOGGER.error("Passwort ändern: " + change.cause());
                    }
                });
            }
        });
    }

    private void uptAdresse(Message<JsonObject> message) {
        LOGGER.info("Adresse wird geändert");
        String name = message.body().getString("name");
        String adresse = message.body().getString("adresse");

        dbClient.getConnection(res -> {
            if (res.succeeded()) {
                SQLConnection connection = res.result();
                connection.execute("update user set adresse = " + "'" + adresse + "'" + " where name = " + "'" + name + "'" + "", change -> {
                    if (change.succeeded()) {
                        message.reply(new JsonObject().put("ÄnderrungAdresse", "es tut"));
                        LOGGER.info("ADRESSE: erfolgreich");
                    } else {
                        message.reply(new JsonObject().put("ÄnderrungAdresse", "tut nicht"));
                        LOGGER.error("" + change.cause());
                    }
                });
            }
        });
    }
}
