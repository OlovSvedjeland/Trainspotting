import TSim.*;

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

          if (sensorEvent.getStatus() == 0x01); {
            
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
    TSimInterface tsi = TSimInterface.getInstance();

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
}
