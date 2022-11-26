package servlets;

import com.google.gson.Gson;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeoutException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.simple.JSONObject;

@WebServlet(name = "SkiersServlet", value = "/skiers")
public class SkiersServlet extends HttpServlet {

  private final Gson gson = new Gson();
  private final static String QUEUE_NAME = "ride";
  private ConnectionFactory factory = new ConnectionFactory();
  private BlockingQueue<Channel> pool = new LinkedBlockingQueue<>();
  private final int POOL_SIZE = 15;
  private final String IP = "35.91.164.14";
  private final int PORT = 5672;
  private final String USER = "username";
  private final String PASSWORD = "password";

  @Override
  public void init() throws ServletException {
    super.init();
    factory.setHost(IP);
    factory.setPort(PORT);
    factory.setUsername(USER);
    factory.setPassword(PASSWORD);
    try {
      Connection connection = factory.newConnection();
      for(int i = 0; i < POOL_SIZE; i++){
        Channel channel = connection.createChannel();
        pool.add(channel);
      }
    } catch (IOException e) {
      e.printStackTrace();
    } catch (TimeoutException e) {
      e.printStackTrace();
    }
  }
  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse res)
      throws IOException {
    res.setContentType("application/json");
    String urlPath = req.getPathInfo();
    if (urlPath == null || urlPath.isEmpty()) {
      res.setStatus(HttpServletResponse.SC_NOT_FOUND);
      res.getWriter().write(gson.toJson(new Message("Missing Parameters!")));
      return;
    }
    String[] urlSplit = urlPath.split("/");
    if (isUrlNotValid(urlSplit)) {
      res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      res.getWriter().write(gson.toJson(new Message("Invalid Inputs! Or Invalid Path! ")));
    } else {
      res.setStatus(HttpServletResponse.SC_OK);
    }
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse res)
      throws IOException, NumberFormatException {
    res.setContentType("application/json");
    String urlPath = req.getPathInfo();
    if (urlPath == null || urlPath.isEmpty()) {
      res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      res.getWriter().write(gson.toJson(new Message("Missing Parameters!")));
      return;
    }
    String[] urlPaths = urlPath.split("/");
    JsonObject body = gson.fromJson(req.getReader(), JsonObject.class);
    if (isUrlNotValid(urlPaths) || isBodyNotValid(body)) {
      res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      res.getWriter().write(gson.toJson(new Message("Invalid Inputs! Or Invalid Path!")));
    } else {
      Integer skierID = Integer.parseInt(urlPaths[7]);
      String resortId = urlPaths[1];
      String seasonId = urlPaths[3];
      String dayId = urlPaths[5];

      JSONObject message = createMessage(body, skierID, dayId, resortId, seasonId);
      try {
        System.out.println("Starting pushing messages");
        Channel channel = pool.take();
        channel.queueDeclare(QUEUE_NAME, false, false, false, null);
        channel.basicPublish("", QUEUE_NAME, null, message.toString().getBytes(StandardCharsets.UTF_8));
        pool.add(channel);
        System.out.println("Success");
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
      res.setStatus(HttpServletResponse.SC_CREATED);
      res.getWriter().write(gson.toJson(new Message("Record is created in the database.")));

    }
  }

  private boolean isBodyNotValid(JsonObject body) {
    JsonElement time = body.get("time");
    JsonElement liftID = body.get("liftID");

    return time == null || liftID == null
        || time.getAsInt() < 1 || time.getAsInt() > 360
        || liftID.getAsInt() < 1 || liftID.getAsInt() > 40;
  }

  private boolean isUrlNotValid(String[] urlPath) {
    int resortId = Integer.parseInt(urlPath[1]);
    int seasonId = Integer.parseInt(urlPath[3]);
    int dayId = Integer.parseInt(urlPath[5]);
    int skierId = Integer.parseInt(urlPath[7]);
    return urlPath.length != 8
        || resortId < 1 || resortId > 10
        || !urlPath[2].equals("seasons")
        || seasonId != 2022
        || !urlPath[4].equals("days")
        || dayId != 1
        || !urlPath[6].equals("skiers")
        || skierId < 1 || skierId > 100000;
  }

  private JSONObject createMessage(JsonObject body, Integer skierID, String dayId, String resortID, String seasonID) {
    JSONObject message = new JSONObject();
    message.put("liftID", body.get("liftID").getAsInt());
    message.put("time", body.get("time").getAsInt());
    message.put("skierID", skierID);
    message.put("dayId", dayId);
    message.put("resortID", resortID);
    message.put("seasonID", seasonID);
    return message;
  }
}
