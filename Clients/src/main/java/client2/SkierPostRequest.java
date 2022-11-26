package client2;

import io.swagger.client.model.LiftRide;
import java.util.concurrent.ThreadLocalRandom;

public class SkierPostRequest {

  public LiftRide liftRide;
  public int resortID;
  public String seasonID;
  public String dayID;
  public int skierID;

  public SkierPostRequest() {
    int skierID = ThreadLocalRandom.current().nextInt(1, 100001);
    int resortID = ThreadLocalRandom.current().nextInt(1, 11);
    int liftID = ThreadLocalRandom.current().nextInt(1, 41);
    int seasonID = 2022;
    int dayID = 1;
    int time = ThreadLocalRandom.current().nextInt(1, 361);

    this.liftRide = new LiftRide().time(time).liftID(liftID);
    this.resortID = resortID;
    this.seasonID = String.valueOf(seasonID);
    this.dayID = String.valueOf(dayID);
    this.skierID = skierID;
  }
}
