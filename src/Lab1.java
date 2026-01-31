import TSim.*;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

// Use semaphores to "lock" tracks so that the trains never occupy the same track, be it parallel or singular.
// If a part of track is locked, and it has a parallel track, use that one.
// Every part of the track that leads to a station should be represented by a semaphore.
// The trains should recognize the semaphore and (in case of station tracks) continue forward.
// Should every switch return to original state when a train has passed?

// Problems:
// Trains go towards the same station, a faster train should be able to overtake a slower one.
// If a critical rail (station rail for example) is occupied, other train should account for this.
// Solve for large speed differences, especially regarding the parallel tracks. Minimum max-speed = 15.
// How does a train know when to slow down, change tracks, stop etc. etc. Through semaphores, but how.

// semaphore.tryAquire might be useful. semaphore.acquire blocks if no permit is available.
// Should the semaphores control certain switches? One semaphore per switch at least?

public class Lab1 {

  public TSimInterface tsi = TSimInterface.getInstance();
  Map<Point, Semaphore> semaphoreMap;

  public Point getSensorPoint(SensorEvent e) {
    return new Point(e.getXpos(), e.getYpos());
  }

  public class TrainController implements Runnable {
    int id;
    int speed;

    TrainController(int id, int speed) {
      this.id = id;
      this.speed = speed;
    }

    public void run() {

      while (true) {

        try {
          SensorEvent sensorEvent = tsi.getSensor(id);

          if (sensorEvent.getStatus() == 0x01) {
            if (isStationSensor(sensorEvent)) {
              // sleep 2 sekunder (kanske en variabel "leaving")
            }
            Point sensPoint = getSensorPoint(sensorEvent);
            Semaphore semaphore = semaphoreMap.get(sensPoint);

            tsi.setSpeed(id, 0);

            if (semaphore.tryAcquire()) {
              tsi.setSpeed(id, speed);
            }

            // switcha spår om det finns och åk där
            else if (sensPoint.equals(new Point(18,7))) { // sensor inför förgrening 1
              tsi.setSwitch(17,7,0x01);
            }

            // annars sleep tills det går att acquire
          }

        } catch (CommandException e) {
          throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
      }
    }
  }

  public Lab1(int speed1, int speed2) {

    this.semaphoreMap = new HashMap<>();

    Semaphore semA = new Semaphore(1);
    Semaphore semB = new Semaphore(1);
    Semaphore semC = new Semaphore(1);
    Semaphore semD = new Semaphore(1);

    // side semaphores
    Semaphore semE = new Semaphore(1);
    Semaphore semF = new Semaphore(1);

    // cross-roads 1-4
    semaphoreMap.put(new Point(8,6), semB);
    semaphoreMap.put(new Point(7,7), semB);
    semaphoreMap.put(new Point(9,7), semA);
    semaphoreMap.put(new Point(8,8), semA);

    // förgrening 1
    semaphoreMap.put(new Point(16, 7), semE);
    semaphoreMap.put(new Point(17, 8), semB);
    semaphoreMap.put(new Point(18, 7), semB);

    // förgrening 2
    semaphoreMap.put(new Point(16, 9), semC);
    semaphoreMap.put(new Point(15, 10), semE);
    semaphoreMap.put(new Point(14, 9), semE);

    //förgrening 3
    semaphoreMap.put(new Point(5, 9), semF);
    semaphoreMap.put(new Point(4, 10), semF);
    semaphoreMap.put(new Point(3, 9), semC);

    //förgrening 4
    semaphoreMap.put(new Point(2, 11), semD);
    semaphoreMap.put(new Point(3, 12), semF);
    semaphoreMap.put(new Point(4, 11), semF);

    try {
      tsi.setSpeed(1,speed1);
      tsi.setSpeed(2,speed2);
    }
    catch (CommandException e) {
      e.printStackTrace();    // or only e.getMessage() for the error
      System.exit(1);
    }

    Thread trainA = new Thread(new TrainController(1,speed1));
    Thread trainB = new Thread(new TrainController(2,speed2));

    trainA.start();
    trainB.start();

  }

  boolean isStationSensor(SensorEvent e) {
    Point p = new Point(e.getXpos(),e.getYpos());

    Point stationSensorA1 = new Point(15,3);
    Point stationSensorA2 = new Point(15, 5);
    Point stationSensorB1 = new Point(15,11);
    Point stationSensorB2 = new Point(15,13);
    return p.equals(stationSensorA1) || p.equals(stationSensorA2)|| p.equals(stationSensorB1) || p.equals(stationSensorB2);
  }

  public Point findSwitch(Point point) {
    return new Point(0,0);
  }

}



/*
switchA 17,7
switchB 15,9
switchC 4,9
switchD 3,11
 */