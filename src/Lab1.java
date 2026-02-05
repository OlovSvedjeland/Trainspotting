import TSim.*;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.Semaphore;

public class Lab1 {

    public TSimInterface tsi = TSimInterface.getInstance();
    Map<Point, List<Semaphore>> downSemMap;
    Map<Point, List<Semaphore>> upSemMap;
    Map<Point, SwitchCmd> switchMap;
    Set<Point> stationSensors;
    Semaphore semI = new Semaphore(1);

    public Point getSensorPoint(SensorEvent e) {
        return new Point(e.getXpos(), e.getYpos());
    }

    public enum TrainDirection {
        UP, DOWN
    }

    public Lab1(int speed1, int speed2) {

        this.downSemMap = new HashMap<>();
        this.upSemMap = new HashMap<>();
        this.switchMap = new HashMap<>();
        this.stationSensors = new HashSet<>();

        TrackBuilder.build(downSemMap, switchMap, stationSensors, upSemMap, semI);

        try {
            tsi.setSpeed(1, speed1);
            tsi.setSpeed(2, speed2);
        } catch (CommandException e) {
            e.printStackTrace();    // or only e.getMessage() for the error
            System.exit(1);
        }

        Thread trainA = new Thread(new TrainController(1, speed1, TrainDirection.DOWN));
        Thread trainB = new Thread(new TrainController(2, speed2, TrainDirection.UP));

        trainA.start();
        trainB.start();
    }

    public class TrainController implements Runnable {
        int id;
        int speed;
        TrainDirection trainDirection;
        Queue<Semaphore> semQueue;

        TrainController(int id, int speed, TrainDirection trainDirection) {
            this.id = id;
            this.speed = speed;
            this.trainDirection = trainDirection;
            this.semQueue = new LinkedList<>();
        }

        public void sleepAndTurn() throws RuntimeException, CommandException {
            tsi.setSpeed(id, 0);

            try {
                Thread.sleep(1000 + (20 * Math.abs(speed)));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            if (trainDirection == TrainDirection.UP) {
                trainDirection = TrainDirection.DOWN;
            } else {
                trainDirection = TrainDirection.UP;
            }
            speed = -speed;
            tsi.setSpeed(id, speed);
        }

        public void run() {



            while (true) {
                try {
                    SensorEvent sensorEvent = tsi.getSensor(id); // Get sensorEvent
                    Point sensPoint = getSensorPoint(sensorEvent); // Get point of sensor for event


                    if (sensorEvent.getStatus() == 0x02 && stationSensors.contains(sensPoint)) {
                        List<Semaphore> semaphores;
                        if (trainDirection == TrainDirection.DOWN) { // Use different maps for acquiring/release
                            semaphores = downSemMap.get(sensPoint); // dependent on direction
                        } else {
                            semaphores = upSemMap.get(sensPoint);
                        }
                        Semaphore sem = semaphores.get(0);
                        if (sem.tryAcquire()) {
                            semQueue.add(sem);
                        }
                        tsi.setSpeed(id, speed);

                        continue;
                    }



                    if (sensorEvent.getStatus() == 0x01) { // If sensor = ACTIVE

                        if (stationSensors.contains(sensPoint)) {
                            sleepAndTurn();
                            continue;
                        }

                            maybeRelease(sensPoint); // Conditional release of semaphores

                            List<Semaphore> semaphores;

                            if (trainDirection == TrainDirection.DOWN) { // Use different maps for acquiring/release
                                semaphores = downSemMap.get(sensPoint); // dependent on direction
                            } else {
                                semaphores = upSemMap.get(sensPoint);
                            }

                            if (semaphores == null || semaphores.isEmpty()) {
                                continue;
                            }

                            SwitchCmd switchCmd = switchMap.get(sensPoint); // Default state of points switch
                            // If 1 semaphore possible, set speed 0 and acquire
                            if (semaphores.size() == 1) {
                                Semaphore sem = semaphores.get(0);
                                tsi.setSpeed(id, 0);
                                sem.acquire();
                                if (switchCmd != null) {
                                    tsi.setSwitch(switchCmd.x, switchCmd.y, switchCmd.dir);
                                }
                                tsi.setSpeed(id, speed);
                                semQueue.add(sem); // Add acquired to queue
                            }
                            // If two semaphores possible, first has permit, acquire
                            if (semaphores.size() == 2 && semaphores.get(0).availablePermits() == 1) {
                                Semaphore sem = semaphores.get(0);
                                sem.acquire();
                                if(switchCmd != null) {
                                    tsi.setSwitch(switchCmd.x, switchCmd.y, switchCmd.dir);
                                }
                                semQueue.add(sem);
                                // If two semaphores possible, second has permit, acquire, switch rail.
                            } else if (semaphores.size() == 2 && semaphores.get(0).availablePermits() == 0) {
                                Semaphore sem = semaphores.get(1);
                                sem.acquire();
                                if (switchCmd != null) {
                                    SwitchCmd.tryOtherDir(tsi, switchCmd.dir, switchCmd);
                                }
                                semQueue.add(sem);
                            }
                    }
                } catch (CommandException e) {
                    throw new RuntimeException(e);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        // Uses opposite of standard directional map for given train to release semaphores.
        public void maybeRelease(Point sensPoint) {

            List<Semaphore> sems;
            if (trainDirection == TrainDirection.UP) {
                sems = downSemMap.get(sensPoint);
            } else sems = upSemMap.get(sensPoint);

            if (sems == null || sems.isEmpty() || semQueue.isEmpty()) {
                return;
            }
            // Crossroads handling, removes no matter placement in queue.
            if (sems.contains(semI)) {
                if(semQueue.remove(semI)) {
                    semQueue.remove(semI);
                    semI.release();
                }
                return;
            }

            Semaphore relevantSem = semQueue.peek();
            // Removes first semaphore in queue and releases.
            if (sems.contains(relevantSem)) {
                semQueue.remove();
                if(relevantSem != null) {
                    relevantSem.release();
                }
            }
        }
    }

    static class SwitchCmd {
        int x, y, dir;

        SwitchCmd(int x, int y, int dir) {
            this.x = x;
            this.y = y;
            this.dir = dir;
        }
        // Invert direction of switch.
        static void tryOtherDir(TSimInterface tsi, int dir, SwitchCmd switchCmd) throws CommandException {
            if (dir == 0x01) {
                tsi.setSwitch(switchCmd.x, switchCmd.y, 0x02);
            } else {
                tsi.setSwitch(switchCmd.x, switchCmd.y, 0x01);
            }
        }
    }
    // Builder for maps, points and semaphores
    static class TrackBuilder {

        static void build(Map<Point, List<Semaphore>> downSemMap,
                          Map<Point, SwitchCmd> switchMap,
                          Set<Point> stationSensors, Map<Point, List<Semaphore>> upSemMap, Semaphore semI) {

            Semaphore semA = new Semaphore(1);
            Semaphore semB = new Semaphore(1);
            Semaphore semC = new Semaphore(1);
            Semaphore semD = new Semaphore(1);
            Semaphore semE = new Semaphore(1);
            Semaphore semF = new Semaphore(1);
            Semaphore semG = new Semaphore(1);
            Semaphore semH = new Semaphore(1);

            // non-station sensors
            Point pointA1 = new Point(6, 3);
            Point pointA2 = new Point(10, 7);
            Point pointA3 = new Point(14, 7);

            Point pointB1 = new Point(8, 5);
            Point pointB2 = new Point(10, 8);
            Point pointB3 = new Point(15, 8);

            Point pointC1 = new Point(19, 9);
            Point pointC2 = new Point(18,9);

            Point pointD1 = new Point(12, 9);
            Point pointD2 = new Point(7, 9);

            Point pointE1 = new Point(13, 10);
            Point pointE2 = new Point(6, 10);

            Point pointF1 = new Point(1, 9);
            Point pointF2 = new Point(1,10);

            Point pointG1 = new Point(6, 11);

            Point pointH1 = new Point(4, 13);

            // station sensors
            Point stationSensorA1 = new Point(15, 3);
            Point stationSensorA2 = new Point(15, 5);
            Point stationSensorB1 = new Point(15, 11);
            Point stationSensorB2 = new Point(15, 13);

            //What semaphores to use depending on direction of train:

            // DOWN
            // branchA (17,7)
            downSemMap.put(pointB3, List.of(semC));
            downSemMap.put(pointA3, List.of(semC));
            // branchB (15,9)
            downSemMap.put(pointC2, List.of(semD, semE));
            // branchC (4,9)
            downSemMap.put(pointD2, List.of(semF));
            downSemMap.put(pointE2, List.of(semF));
            // branchD (3,11)
            downSemMap.put(pointF2, List.of(semG, semH));
            // crossroads
            downSemMap.put(pointB1, List.of(semI));
            downSemMap.put(pointA1, List.of(semI));
            //station
            downSemMap.put(stationSensorA1, List.of(semA));
            downSemMap.put(stationSensorA2, List.of(semB));

            // UP
            // branchD (3,11)
            upSemMap.put(pointH1, List.of(semF));
            upSemMap.put(pointG1, List.of(semF));
            // branchC (4,9)
            upSemMap.put(pointF1, List.of(semD, semE));
            // branchB (15,9)
            upSemMap.put(pointD1, List.of(semC));
            upSemMap.put(pointE1, List.of(semC));
            // branchA (17,7)
            upSemMap.put(pointC1, List.of(semA, semB));
            // crossroads
            upSemMap.put(pointB2, List.of(semI));
            upSemMap.put(pointA2, List.of(semI));
            // stationsensors
            upSemMap.put(stationSensorB1, List.of(semG));
            upSemMap.put(stationSensorB2, List.of(semH));


            // switches
            SwitchCmd branchA_right = new SwitchCmd(17, 7, 0x02);
            SwitchCmd branchA_left = new SwitchCmd(17, 7, 0x01);
            SwitchCmd branchB_right = new SwitchCmd(15, 9, 0x02);
            SwitchCmd branchB_left = new SwitchCmd(15, 9, 0x01);
            SwitchCmd branchC_right = new SwitchCmd(4, 9, 0x02);
            SwitchCmd branchC_left = new SwitchCmd(4, 9, 0x01);
            SwitchCmd branchD_right = new SwitchCmd(3, 11, 0x02);
            SwitchCmd branchD_left = new SwitchCmd(3, 11, 0x01);

            // branchA (17,7)
            switchMap.put(pointA3, branchA_right);
            switchMap.put(pointB3, branchA_left);
            switchMap.put(pointC1, branchA_right);

            // branchB (15,9)
            switchMap.put(pointC2, branchB_right);
            switchMap.put(pointD1, branchB_right);
            switchMap.put(pointE1, branchB_left);

            // branchC (4,9)
            switchMap.put(pointD2, branchC_left);
            switchMap.put(pointE2, branchC_right);
            switchMap.put(pointF1, branchC_left);

            // branchD (3,11)
            switchMap.put(pointF2, branchD_left);
            switchMap.put(pointG1, branchD_left);
            switchMap.put(pointH1, branchD_right);


            // stationSensors - up / down
            stationSensors.add(stationSensorA1);
            stationSensors.add(stationSensorA2);
            stationSensors.add(stationSensorB1);
            stationSensors.add(stationSensorB2);

        }
    }
}
