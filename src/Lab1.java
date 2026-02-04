import TSim.*;

import java.awt.*;
import java.util.*;
import java.util.List;
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
// Should the semaphores control certain switches? One semaphore per switch at least? Sensors have one or several
// semaphores connected, depending on which are available for it to access.

// Something weird with the switching: The trains had their directions inverted to how they actually travel, FIXED
// The switches directions are sometimes inverted to their actual dir. Might be a map problem? Or switch method problem?
// Sometimes when the terminal says that a RIGHT switch is performed, a LEFT switch is instead performed.
// And sometimes nothing happens at all.

public class Lab1 {

    public TSimInterface tsi = TSimInterface.getInstance();
    Map<Point, List<Semaphore>> semaphoreMap;
    Map<Point, List<Semaphore>> upSemMap;
    Map<Point, SwitchCmd> switchMap;
    Set<Point> stationSensors;
    Set<Point> upReleaseSensors;

    public Point getSensorPoint(SensorEvent e) {
        return new Point(e.getXpos(), e.getYpos());
    }

    public enum TrainDirection {
        UP, DOWN
    }

    public Lab1(int speed1, int speed2) {

        this.semaphoreMap = new HashMap<>();
        this.upSemMap = new HashMap<>();
        this.switchMap = new HashMap<>();
        this.stationSensors = new HashSet<>();
        this.upReleaseSensors = new HashSet<>();

        TrackBuilder.build(semaphoreMap, switchMap, stationSensors, upReleaseSensors, upSemMap);

        try {
            tsi.setSpeed(1, speed1);
            tsi.setSpeed(2, speed2);
        } catch (CommandException e) {
            e.printStackTrace();    // or only e.getMessage() for the error
            System.exit(1);
        }

        // Minns ej vilket tåg som var vilket????
        Thread trainA = new Thread(new TrainController(1, speed1, TrainDirection.DOWN));
        Thread trainB = new Thread(new TrainController(2, speed2, TrainDirection.UP));

        trainA.start();
        trainB.start();
    }

    public class TrainController implements Runnable {
        int id;
        int speed;
        TrainDirection trainDirection;
        Stack<Semaphore> semStack;
        Queue<Semaphore> semQueue;

        TrainController(int id, int speed, TrainDirection trainDirection) {
            this.id = id;
            this.speed = speed;
            this.trainDirection = trainDirection;
            this.semStack = new Stack<>();
            this.semQueue = new LinkedList<>();
        }

        public void sleepAndTurn() throws RuntimeException, CommandException {

            int tempSpeed = speed;
            System.out.println("sleeeeep and tuuuuurn");
            tsi.setSpeed(id, 0);

            try {
                Thread.sleep(1000 + (20 * Math.abs(tempSpeed)));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            if (trainDirection == TrainDirection.UP) {
                trainDirection = TrainDirection.DOWN;
            } else {
                trainDirection = TrainDirection.UP;
            }
            tsi.setSpeed(id, -tempSpeed);
        }

        public void run() {

            while (true) {
                try {
                    SensorEvent sensorEvent = tsi.getSensor(id);
                    Point sensPoint = getSensorPoint(sensorEvent);

                    if (sensorEvent.getStatus() == 0x01) {

                        if (stationSensors.contains(sensPoint)) {
                            sleepAndTurn();
                            continue;
                        }

                            List<Semaphore> semaphores;

                            if (trainDirection == TrainDirection.DOWN) {
                                System.out.println("Train Direction = DOWN");
                                semaphores = semaphoreMap.get(sensPoint);
                            } else {
                                System.out.println("Train Direction = DOWN");
                                semaphores = upSemMap.get(sensPoint);
                            }

                            if (semaphores == null || semaphores.isEmpty()) {
                                // throw new RuntimeException("missing semaphore");
                                continue;
                            }

                            SwitchCmd switchCmd = switchMap.get(sensPoint);
                            if (switchCmd == null) {
                                throw new RuntimeException("missing switchCmd");
                            }

                            tsi.setSpeed(id, 0);

                            if (semaphores.get(0).tryAcquire()) {
                                int availableSem = semaphores.get(0).availablePermits();
                                System.out.println(availableSem);
                                tsi.setSpeed(id, speed);
                                tsi.setSwitch(switchCmd.x, switchCmd.y, switchCmd.dir);
                                semQueue.add(semaphores.get(0)); // was getFirst()

                            } else if (semaphores.size() > 1) {
                                semaphores.get(1).acquire();
                                SwitchCmd.tryOtherDir(tsi, switchCmd.dir, switchCmd);
                                tsi.setSpeed(id, speed);
                                semQueue.add(semaphores.get(1));
                            }


                            if ((isUpReleaseSensor(sensPoint) && trainDirection == TrainDirection.UP)
                                    || (!isUpReleaseSensor(sensPoint) && trainDirection == TrainDirection.DOWN)) {
                                Semaphore sem = semQueue.remove();
                                sem.release();
                            }



                            int availableSem = semaphores.get(0).availablePermits();
                            System.out.println(availableSem);
                    }
                } catch (CommandException e) {
                    throw new RuntimeException(e);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        public void release(TrainDirection trainDirection, Point sensor) {
            switch (trainDirection) {
                // TODO
            }
        }

        boolean isUpReleaseSensor(Point sensPoint) {
            return upReleaseSensors.contains(sensPoint);
        }
    }

    static class SwitchCmd {
        int x, y, dir;

        SwitchCmd(int x, int y, int dir) {
            this.x = x;
            this.y = y;
            this.dir = dir;
        }

        static void tryOtherDir(TSimInterface tsi, int dir, SwitchCmd switchCmd) throws CommandException {
            if (dir == 0x01) {
                tsi.setSwitch(switchCmd.x, switchCmd.y, 0x02);
            } else {
                tsi.setSwitch(switchCmd.x, switchCmd.y, 0x01);
            }
        }
    }

    static class TrackBuilder {

        static void build(Map<Point, List<Semaphore>> semaphoreMap,
                          Map<Point, SwitchCmd> switchMap,
                          Set<Point> stationSensors,
                          Set<Point> upReleaseSensor, Map<Point, List<Semaphore>> upSemMap) {

            Semaphore semA = new Semaphore(1);
            Semaphore semB = new Semaphore(1);
            Semaphore semC = new Semaphore(1);
            Semaphore semD = new Semaphore(1);
            Semaphore semE = new Semaphore(1);
            Semaphore semF = new Semaphore(1);
            Semaphore semG = new Semaphore(1);
            Semaphore semH = new Semaphore(1);
            Semaphore semI = new Semaphore(1);

            Point pointA1 = new Point(6, 7);
            Point pointA2 = new Point(10, 7);
            Point pointA3 = new Point(15, 7);

            Point pointB1 = new Point(8, 5);
            Point pointB2 = new Point(9, 8);
            Point pointB3 = new Point(16, 8);

            Point pointC1 = new Point(19, 9);
            Point pointC2 = new Point(18,9);

            Point pointD1 = new Point(13, 9);
            Point pointD2 = new Point(6, 9);

            Point pointE1 = new Point(14, 10);
            Point pointE2 = new Point(5, 10);

            Point pointF1 = new Point(1, 9);
            Point pointF2 = new Point(1,10);

            Point pointG1 = new Point(5, 11);

            Point pointH1 = new Point(3, 13);

            // DOWNMAP
            // förgrening 1
            semaphoreMap.put(pointB3, List.of(semC));
            semaphoreMap.put(pointA3, List.of(semC));
            // förgrening 2
            semaphoreMap.put(pointC1, List.of(semD, semE));
            // förgrening 3
            semaphoreMap.put(pointD2, List.of(semF));
            semaphoreMap.put(pointE2, List.of(semF));
            // förgrening 4
            semaphoreMap.put(pointF1, List.of(semG, semH));
            semaphoreMap.put(pointG1, List.of(semF));
            semaphoreMap.put(pointH1, List.of(semF));

            // UPMAP
            upSemMap.put(pointH1, List.of(semF));
            upSemMap.put(pointG1, List.of(semF));
            upSemMap.put(pointF1, List.of(semD, semE));
            upSemMap.put(pointD1, List.of(semC));
            upSemMap.put(pointE1, List.of(semC));
            upSemMap.put(pointC1, List.of(semA, semB));



            // switches
            SwitchCmd branchA = new SwitchCmd(17, 7, 0x02);
            SwitchCmd branchB = new SwitchCmd(15, 9, 0x02);
            SwitchCmd branchC = new SwitchCmd(4, 9, 0x01);
            SwitchCmd branchD = new SwitchCmd(3, 11, 0x01);

            // Förgrening 1, branchA (17,7)
            switchMap.put(pointA3, branchA);
            switchMap.put(pointB3, branchA);
            switchMap.put(pointC1, branchA);

            // Förgrening 2 → branchB (15,9)
            switchMap.put(pointC2, branchB);
            switchMap.put(pointD1, branchB);
            switchMap.put(pointE1, branchB);

            // Förgrening 3, branchC (4,9)
            switchMap.put(pointD2, branchC);
            switchMap.put(pointE2, branchC);
            switchMap.put(pointF1, branchC);

            // Förgrening 4, branchD (3,11)
            switchMap.put(pointF2, branchD);
            switchMap.put(pointG1, branchD);
            switchMap.put(pointH1, branchD);

            // stationSensors - up / down
            stationSensors.add(new Point(15, 3));
            stationSensors.add(new Point(15, 5));
            stationSensors.add(new Point(15, 11));
            stationSensors.add(new Point(15, 13));

            upReleaseSensor.add(pointF1);
            upReleaseSensor.add(pointD2);
            upReleaseSensor.add(pointE2);
            upReleaseSensor.add(pointC1);
            upReleaseSensor.add(pointA3);
            upReleaseSensor.add(pointB3);
            upReleaseSensor.add(pointA1);
            upReleaseSensor.add(pointB1);

            /*
            upReleaseSensor.add(new Point(15, 3));
            upReleaseSensor.add(new Point(15, 5));
            upReleaseSensor.add(new Point(15, 11));
            upReleaseSensor.add(new Point(15, 13));
            upReleaseSensor.add(new Point(15, 3));
            upReleaseSensor.add(new Point(15, 3));
            upReleaseSensor.add(new Point(15, 3));
            upReleaseSensor.add(new Point(15, 3));
            */
        }
    }
}
