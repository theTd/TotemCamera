package org.totemcraft.camera;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mineclay.tclite.command.*;
import lombok.Data;
import lombok.SneakyThrows;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;

public final class Camera extends JavaPlugin {
    double lambda = 0.001;
    //用于存储点坐标的简单类
    @Data
    public class Point {
        private final double x;
        private final double y;
        private final double z;
        private final double yaw;  // 虽然原来是float，但我们存为double类型
        private final double pitch;  // 虽然原来是float，但我们存为double类型

        //获取X坐标
        public double x() {
            return x;
        }

        //获取Y坐标
        public double y() {
            return y;
        }

        //获取Z坐标
        public double z() {
            return z;
        }

        //获取yaw值
        public double yaw() {   //返回值也改为double类型
            return yaw;
        }

        //获取pitch值
        public double pitch() {   //返回值也改为double类型
            return pitch;
        }
    }

    public class PointSequence {
        private Point[] sequence;


        public PointSequence() {
            sequence = new Point[0];
        }

        public Point[] array() {
            return sequence;
        }

        public Point getFirst() {
            return sequence[0];
        }

        public Point getLast() {
            return sequence[sequence.length - 1];
        }

        public void addPoints(Point[] p) {
            Point[] out = Arrays.copyOf(sequence, sequence.length + p.length);
            System.arraycopy(p, 0, out, sequence.length, p.length);
            sequence = out;
        }

        public void addPoints(Point p) {
            Point[] out = Arrays.copyOf(sequence, sequence.length + 1);
            out[sequence.length] = p;
            sequence = out;
        }
    }


    public PointSequence catmullRomLine(Point P0, Point P1, Point P2, Point P3, double lambda) {

        // 输入lambda值代表每1方块单位长度绘制lambda个点
        //lambda *= distance(P1.x(), P1.y(), P2.x(), P2.y());

        // 插值点的个数
        int n = (int) Math.ceil(1 / lambda);

        // 曲线扭曲度参数alpha
        double alpha = 0.5;

        // 创建输出
        PointSequence sequence = new PointSequence();

        for (int i = 0; i <= n; i++) {
            // 计算插值参数t
            double t = i * lambda;

            // 使用 Catmull-Rom 公式计算插值点
            Point point = new Point(
                    interpolate(alpha, t, P0.x(), P1.x(), P2.x(), P3.x()),
                    interpolate(alpha, t, P0.y(), P1.y(), P2.y(), P3.y()),
                    interpolate(alpha, t, P0.z(), P1.z(), P2.z(), P3.z()),
                    interpolate(alpha, t, P0.yaw(), P1.yaw(), P2.yaw(), P3.yaw()),
                    interpolate(alpha, t, P0.pitch(), P1.pitch(), P2.pitch(), P3.pitch())
            );

            sequence.addPoints(point);
        }

        return sequence;
    }

    // 针对不同的变量执行插值计算
    private double interpolate(double alpha, double t, double v0, double v1, double v2, double v3) {
        double a = Math.pow(t, 3) * (-alpha * v0 + (2 - alpha) * v1 + (alpha - 2) * v2 + alpha * v3);
        double b = Math.pow(t, 2) * (2 * alpha * v0 + (alpha - 3) * v1 + (3 - 2 * alpha) * v2 - alpha * v3);
        double c = t * (-alpha * v0 + alpha * v2);
        double d = v1;

        // 取余确保结果在指定的范围内
        return (a + b + c + d) % 360;
    }

    private double distance(double x1, double y1, double x2, double y2) {
        return Math.sqrt(Math.pow((x1 - x2), 2) + Math.pow((y1 - y2), 2));
    }


//    public PointSequence catmullRomLine(Point P0, Point P1, Point P2, Point P3, double lambda) {
//
//        //输入的lambda值代表每1方块单位长度绘制lambda个点 但对于这个曲线这样算会有一点误差
//        lambda *= distance(P1.x(), P1.y(), P2.x(), P2.y());//让曲线绘制密度与两点距离成正比,distance函数为欧式距离的算法
//        lambda = 1 / lambda;
//
//        //插值点的个数
//        int n = (int) ceil(1 / lambda);
//
//        //曲线扭曲度参数alpha
//        double alpha = 0.5;
//
//        //创建输出
//        PointSequence sequence = new PointSequence();
//
//        //遍历每个插值点
//        for (int i = 0; i < n; i++) {
//            //计算插值参数t
//            double t = i * lambda;
//
//            //套方程式计算插值点的坐标
//            sequence.addPoints(new Point(
//                    (pow(t, 3) * (-alpha * P0.x() + (2 - alpha) * P1.x() + (alpha - 2) * P2.x() + alpha * P3.x()) + pow(t, 2) * (2 * alpha * P0.x() + (alpha - 3) * P1.x() + (3 - 2 * alpha) * P2.x() - alpha * P3.x()) + t * (-alpha * P0.x() + alpha * P2.x()) + P1.x()),
//                    (pow(t, 3) * (-alpha * P0.y() + (2 - alpha) * P1.y() + (alpha - 2) * P2.y() + alpha * P3.y()) + pow(t, 2) * (2 * alpha * P0.y() + (alpha - 3) * P1.y() + (3 - 2 * alpha) * P2.y() - alpha * P3.y()) + t * (-alpha * P0.y() + alpha * P2.y()) + P1.y()),
//                    (pow(t, 3) * (-alpha * P0.z() + (2 - alpha) * P1.z() + (alpha - 2) * P2.z() + alpha * P3.z()) + pow(t, 2) * (2 * alpha * P0.z() + (alpha - 3) * P1.z() + (3 - 2 * alpha) * P2.z() - alpha * P3.z()) + t * (-alpha * P0.z() + alpha * P2.z()) + P1.z())
//            ));
//        }
//        return sequence;
//    }

    public PointSequence catmullRomConnect(PointSequence input, Point ctrl1, Point ctrl2, double lambda) {
        //转换输入(获取数组形式的点序列)
        Point[] points = input.array();
        //创建输出
        PointSequence output = new PointSequence();

        //判断输入合法度
        //要连接的点必须大于或等于2个
        if (points.length > 2) {
            for (int i = 0; i < points.length - 1; i++) {
                if (i == 0) {//第一个点和第二个点 前一个控制点取ctrl1
                    output.addPoints(catmullRomLine(ctrl1, points[i], points[i + 1], points[i + 2], lambda).array());
                    continue;
                }
                if (i == points.length - 2) {//倒数第二个点和最后一个点 后一个控制点取ctrl2
                    output.addPoints(catmullRomLine(points[i - 1], points[i], points[i + 1], ctrl2, lambda).array());
                    break;
                }
                //一般情况
                output.addPoints(catmullRomLine(points[i - 1], points[i], points[i + 1], points[i + 2], lambda).array());
            }
        }
        //当只需要连接2个点时 控制点分别为ctrl1和ctrl2 该情况只有一条曲线
        else if (points.length == 2) {
            output.addPoints(catmullRomLine(ctrl1, points[0], points[1], ctrl2, lambda).array());
        } else {
            throw new NullPointerException();//输入不合法
        }

        return output;
    }


    List<Location> posList = new ArrayList<>();
    List<Point> interpolatedPoints = new ArrayList<>();

    final ScheduledExecutorService positionScheduler = Executors.newSingleThreadScheduledExecutor(
            new ThreadFactoryBuilder().setNameFormat("camera-position-scheduler").build());

    final List<PrimaryThreadSynchronizedPositionSender> runningTasks = new ArrayList<>();

    public File getScenesDir() throws IOException {
        File scenesDir = new File(getDataFolder(), "scenes");
        if(scenesDir.isDirectory()||scenesDir.mkdirs()) {
            return scenesDir;
        }
        throw new IOException("failed to create scenes dir");
    }

    final CommandExecutor cameraCommand = new CommandExecutor("camera") {
        {
            childCommand("addpos", (ctx)->{
                Location location = ctx.getPlayer().getLocation();
                posList.add(location);

                // 检测是否有至少4个点，开始计算插值点
                if (posList.size() >= 4) {
                    int lastIndex = posList.size() - 1;
                    Point P0 = toPoint(posList.get(lastIndex - 3));
                    Point P1 = toPoint(posList.get(lastIndex - 2));
                    Point P2 = toPoint(posList.get(lastIndex - 1));
                    Point P3 = toPoint(posList.get(lastIndex));

                    // 将插值点添加到全局列表中
                    interpolatedPoints.addAll(Arrays.asList(catmullRomLine(P0, P1, P2, P3, lambda).array()));
                }

                ctx.sendMessage("[TotemCamera]: 添加第" + posList.size() + "个关键点");
            });
            childCommand("clear", (ctx)->{
                posList.clear();
                ctx.sendMessage("[TotemCamera]: 已清除所有关键点");
            });
            new CommandExecutor(this, "speed") {
                final ArgTokenR<Double> argSpeed = requireArg(ArgParser.DOUBLE, "speed");
                @Override
                public void execute(@NotNull CommandContext ctx) throws CommandSignal {
                    lambda = (ctx.valueOf(argSpeed)) / 1000;
                    ctx.sendMessage("[TotemCamera]: 已设置速度为："+(lambda*1000));
                }
            };
            //保存当前路径为文件
            new CommandExecutor(this, "save") {
                final ArgTokenR<String> argFilename = requireArg(ArgParser.STRING, "filename");
                final ArgTokenR<Boolean> argOverwrite = optionalArg("overwrite").parser((ctx,arg)->"overwrite".equals(arg)).defaultsTo(false);
                @Override
                @SneakyThrows
                public void execute(@NotNull CommandContext ctx) throws CommandSignal {

                    if (posList.size()< 2){
                        throw  error("场景无效");
                    }

                    String fileName = ctx.valueOf(argFilename);
                    boolean overwrite = ctx.valueOf(argOverwrite);
                    File scenceFile = new File(getScenesDir(), fileName+".yml");
                    if(scenceFile.exists()&&!overwrite) {
                        throw error("exists, use overwrite to overwrite");
                    }

                    YamlConfiguration config = new YamlConfiguration();
                    config.set("points", posList);
                    config.save(scenceFile);

                    ctx.sendMessage("[TotemCamera]: 已保存为："+fileName);
                }
            };
            new CommandExecutor(this, "play-scene") {
                final ArgTokenR<Player> argPlayer = requireArg(ArgParser.ONLINE_PLAYER);
                final ArgTokenR<String> argScene = requireArg("scene");
                @Override
                public void execute(@NotNull CommandContext ctx) throws CommandSignal {
                    // todo
                }
            };
            //加载保存在本地的路径
            new CommandExecutor(this, "load") {
                final ArgTokenR<String> argFilename = requireArg(ArgParser.STRING, "filename");
                @Override
                @SneakyThrows
                public void execute(@NotNull CommandContext ctx) throws CommandSignal {
                    String fileName = ctx.valueOf(argFilename);
                    File scenceFile = new File(getScenesDir(), fileName+".yml");
                    if(!scenceFile.exists()) throw error("not found");

                    YamlConfiguration config = YamlConfiguration.loadConfiguration(scenceFile);
                    if(!config.isList("points")) throw error("invalid config");

                    //noinspection unchecked
                    Camera.this. posList = (List<Location>)config.getList("points");

                    ctx.sendMessage("[TotemCamera]: 已加载："+fileName);
                }
            };

            //smooth camera
            childCommand("start",(ctx)->{
                if (posList.size()< 2) {
                    ctx.sendMessage("[TotemCamera]: 关键点必须大于或等于2个");
                    throw  error("关键点必须大于或等于2个");
                }
                PointSequence ps = new PointSequence();
                posList.forEach(element -> {
                    double pos1 = element.getBlockX();
                    double pos2 = element.getBlockY();
                    double pos3 = element.getBlockZ();
                    float yaw = element.getYaw();
                    float pitch = element.getPitch();
                    ps.addPoints(new Point(pos1, pos2, pos3, yaw, pitch));
                });
                PointSequence result = catmullRomConnect(ps, ps.getFirst(), ps.getLast(), lambda);

                Point[] teleportPoints = result.array();

                List<Point> points = Arrays.stream(teleportPoints).collect(Collectors.toList());

                PrimaryThreadSynchronizedPositionSender task = new PrimaryThreadSynchronizedPositionSender(ctx.getPlayer(), points);
                long frameRate = 100;
                long intervalMs = 1000 / frameRate;
                task.schedule = positionScheduler.scheduleAtFixedRate(task, 0, intervalMs, TimeUnit.MILLISECONDS);
                runningTasks.add(task);
            });

            //legacy camera use tp
            childCommand("start-legacy",(ctx)->{
                if (posList.size()< 2) {
                    ctx.sendMessage("[TotemCamera]: 关键点必须大于或等于2个");
                    throw  error("关键点必须大于或等于2个");
                }
                PointSequence ps = new PointSequence();
                posList.forEach(element -> {
                    double pos1 = element.getBlockX();
                    double pos2 = element.getBlockY();
                    double pos3 = element.getBlockZ();
                    float yaw = element.getYaw();
                    float pitch = element.getPitch();
                    ps.addPoints(new Point(pos1, pos2, pos3, yaw, pitch));
                });
                PointSequence result = catmullRomConnect(ps, ps.getFirst(), ps.getLast(), lambda);

                Point[] teleportPoints = result.array();

                List<Point> points = Arrays.stream(teleportPoints).collect(Collectors.toList());

//                PrimaryThreadSynchronizedPositionSender task = new PrimaryThreadSynchronizedPositionSender(ctx.getPlayer(), points);
//                long frameRate = 100;
//                long intervalMs = 1000 / frameRate;
//                task.schedule = positionScheduler.scheduleAtFixedRate(task, 0, intervalMs, TimeUnit.MILLISECONDS);
//                runningTasks.add(task);
//
//                Bukkit.getScheduler().runTaskTimer(Camera.this, (task)->{
//                    if (pointIterator.hasNext()) {
//                        Point nextPoint = pointIterator.next();
//                        ctx.getPlayer().teleport(new Location(ctx.getPlayer().getWorld(), nextPoint.x(), nextPoint.y(), nextPoint.z(), (float) nextPoint.yaw(), (float) nextPoint.pitch()));
//                    }else {
//                        task.cancel();
//                    }
//                },0,1);
            });

        }

        @Override
        public void execute(@NotNull CommandContext ctx) throws CommandSignal {
            throw escapeToHelpMessage();
        }
    };

    private Point toPoint(Location location) {
        return new Point(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
    }
    @Override
    public void onEnable() {
        cameraCommand.register(this);
        getLogger().log(Level.INFO, "[[TotemCamera is loaded]]");
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (PrimaryThreadSynchronizedPositionSender runningTask : runningTasks) {
                runningTask.syncTick();
            }
            runningTasks.removeIf(t -> t.schedule == null || t.schedule.isDone());
        }, 1, 1);
    }

    @SneakyThrows
    @Override
    public void onDisable() {
        positionScheduler.shutdown();
        if (!positionScheduler.awaitTermination(3, TimeUnit.SECONDS)) {
            getLogger().log(Level.WARNING, "position scheduler timed out terminating, killed " + positionScheduler.shutdownNow().size() + " tasks");
        }
    }
}
