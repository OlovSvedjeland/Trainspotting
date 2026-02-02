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

public class Lab1 {

    public TSimInterface tsi = TSimInterface.getInstance();
    Map<Point, List<Semaphore>> semaphoreMap;
    Map<Point, Semaphore> exitSemaphoreMap;
    Map<Point, SwitchCmd> switchMap;
    Set<Point> stationSensors;

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
                    Point sensPoint = getSensorPoint(sensorEvent);

                    // ACTIVE sensor event
                    if (sensorEvent.getStatus() == 0x01) {
                        if (stationSensors.contains(sensPoint)) {
                            // sleep 2 sekunder (kanske en variabel "leaving").
                        }

                        // null checks
                        List<Semaphore> semaphores = semaphoreMap.get(sensPoint);
                        if (semaphores == null || semaphores.isEmpty()) throw new RuntimeException("missing");

                        SwitchCmd switchCmd = switchMap.get(sensPoint);
                        if (switchCmd == null) throw new RuntimeException("missing semaphore");

                        tsi.setSwitch(switchCmd.x, switchCmd.y, switchCmd.dir);

                        tsi.setSpeed(id, 0);

                        if (semaphores.get(0).tryAcquire()) {
                            tsi.setSpeed(id, speed);

                        } else if (semaphores.size() > 1) {
                            semaphores.get(1).acquire();
                            SwitchCmd.tryOtherDir(tsi, switchCmd.dir, switchCmd);
                            tsi.setSpeed(id, speed);
                        }
                    }
                    // DEACTIVE sensor event
                    else {
                        Semaphore releaseSem = exitSemaphoreMap.get(sensPoint);
                        if (releaseSem == null ) throw new RuntimeException("missing semaphore to release");

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
        this.switchMap = new HashMap<>();
        this.stationSensors = new HashSet<>();
        this.exitSemaphoreMap = new HashMap<>();

        TrackBuilder.build(semaphoreMap, switchMap, stationSensors, exitSemaphoreMap);

        try {
            tsi.setSpeed(1, speed1);
            tsi.setSpeed(2, speed2);
        } catch (CommandException e) {
            e.printStackTrace();    // or only e.getMessage() for the error
            System.exit(1);
        }

        Thread trainA = new Thread(new TrainController(1, speed1));
        Thread trainB = new Thread(new TrainController(2, speed2));

        trainA.start();
        trainB.start();

    }

    static class SwitchCmd {
        final int x, y, dir;

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

        static void build(Map<Point, List<Semaphore>> semaphoreMap, Map<Point, SwitchCmd> switchMap, Set<Point> stationSensors, Map<Point, Semaphore> exitSemaphoreMap) {

            Semaphore semA = new Semaphore(1);
            Semaphore semB = new Semaphore(1);
            Semaphore semC = new Semaphore(1);
            Semaphore semD = new Semaphore(1);
            Semaphore semE = new Semaphore(1);
            Semaphore semF = new Semaphore(1);
            Semaphore semG = new Semaphore(1);
            Semaphore semH = new Semaphore(1);

            // förgrening 1
            semaphoreMap.put(new Point(16, 7), List.of(semC));
            semaphoreMap.put(new Point(17, 8), List.of(semC));
            semaphoreMap.put(new Point(18, 7), List.of(semA, semB));

            // förgrening 2
            semaphoreMap.put(new Point(16, 9), List.of(semD, semE));
            semaphoreMap.put(new Point(15, 10), List.of(semC));
            semaphoreMap.put(new Point(14, 9), List.of(semC));

            // förgrening 3
            semaphoreMap.put(new Point(5, 9), List.of(semF));
            semaphoreMap.put(new Point(4, 10), List.of(semF));
            semaphoreMap.put(new Point(3, 9), List.of(semD, semE));

            // förgrening 4
            semaphoreMap.put(new Point(2, 11), List.of(semG, semH));
            semaphoreMap.put(new Point(3, 12), List.of(semF));
            semaphoreMap.put(new Point(4, 11), List.of(semF));

            // Exitmap
            exitSemaphoreMap.put(new  Point(16, 7), semA);
            exitSemaphoreMap.put(new  Point(17,8), semB);
            exitSemaphoreMap.put(new  Point(18, 7), semC);
            exitSemaphoreMap.put(new  Point(16, 9), semC);
            exitSemaphoreMap.put(new  Point(15, 10), semE);
            exitSemaphoreMap.put(new  Point(14, 9), semD);
            exitSemaphoreMap.put(new  Point(5, 9), semD);
            exitSemaphoreMap.put(new  Point(4, 10), semE);
            exitSemaphoreMap.put(new  Point(3, 9), semF);
            exitSemaphoreMap.put(new  Point(2, 11), semF);
            exitSemaphoreMap.put(new  Point(3, 12), semH);
            exitSemaphoreMap.put(new  Point(4, 11), semG);

            // switches
            SwitchCmd branchA = new SwitchCmd(17, 7, 0x02);
            SwitchCmd branchB = new SwitchCmd(15, 9, 0x02);
            SwitchCmd branchC = new SwitchCmd(4, 9, 0x01);
            SwitchCmd branchD = new SwitchCmd(3, 11, 0x02);

            // Förgrening 1, branchA (17,7)
            switchMap.put(new Point(16, 7), branchA);
            switchMap.put(new Point(17, 8), branchA);
            switchMap.put(new Point(18, 7), branchA);

            // Förgrening 2 → branchB (15,9)
            switchMap.put(new Point(16, 9), branchB);
            switchMap.put(new Point(15, 10), branchB);
            switchMap.put(new Point(14, 9), branchB);

            // Förgrening 3, branchC (4,9)
            switchMap.put(new Point(5, 9), branchC);
            switchMap.put(new Point(4, 10), branchC);
            switchMap.put(new Point(3, 9), branchC);

            // Förgrening 4, branchD (3,11)
            switchMap.put(new Point(2, 11), branchD);
            switchMap.put(new Point(3, 12), branchD);
            switchMap.put(new Point(4, 11), branchD);

            // stationSensors - up / down
            stationSensors.add(new Point(15, 3));
            stationSensors.add(new Point(15, 5));
            stationSensors.add(new Point(15, 11));
            stationSensors.add(new Point(15, 13));
        }
    }
}
