package org.totemcraft.camera;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_18_R2.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.joor.Reflect;
import xyz.jpenilla.reflectionremapper.ReflectionRemapper;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

class PrimaryThreadSynchronizedPositionSender implements Runnable {

    final static ReflectionRemapper REFLECTION_REMAPPER = ReflectionRemapper.forReobfMappingsInPaperJar();

    final Player player;
    final Iterator<Camera.Point> points;
    final Camera.Point startPoint;
    ScheduledFuture<?> schedule;

    Camera.Point lastPoint;

    CameraEntity cameraEntity;
    boolean cameraMounted = false;

    boolean finished = false;

    PrimaryThreadSynchronizedPositionSender(Player player, List<Camera.Point> points) {
        System.out.println("points.size() = " + points.size());

        for (int i = 0; i < 10; i++) {
            if(points.size()<=i) break;
            System.out.println("point "+i+" = "+points.get(i));
        }

        this.player = player;
        this.points = points.iterator();
        this.startPoint = this.points.next();
        System.out.println("this.startPoint = " + this.startPoint);
    }

    void initCamera() {
        Camera.Point point = startPoint;
        ServerPlayer nmsPlayer = ((CraftPlayer) player).getHandle();
        cameraEntity = new CameraEntity(nmsPlayer.getLevel(), point.x(),point.y(),point.z(),(float)point.yaw(),(float)point.pitch());
        nmsPlayer.level.addFreshEntity(cameraEntity);
    }

    GameMode originalGameMode;

    void mountCamera() {
        originalGameMode = player.getGameMode();
        player.setGameMode(GameMode.SPECTATOR);
        ServerPlayer nmsPlayer = ((CraftPlayer) player).getHandle();
        FriendlyByteBuf msg = new FriendlyByteBuf(Unpooled.buffer());
        msg.writeVarInt(cameraEntity.getId());
        nmsPlayer.connection.send(new ClientboundSetCameraPacket(msg));
        cameraMounted = true;
    }

    void destroyCamera() {
        ServerPlayer nmsPlayer = ((CraftPlayer) player).getHandle();
        FriendlyByteBuf msg = new FriendlyByteBuf(Unpooled.buffer());
        msg.writeVarInt(nmsPlayer.getId());
        nmsPlayer.connection.send(new ClientboundSetCameraPacket(msg));

        if(cameraEntity!=null) {
            cameraEntity.remove();
        }
        if(originalGameMode!=null) player.setGameMode(originalGameMode);
    }

    void syncTick() {
        if(finished) {
            destroyCamera();
            return;
        }
        if(cameraEntity == null) {
            initCamera();
            return;
        }
        if (!cameraMounted) {
            mountCamera();
            return;
        }

        Camera.Point syncPoint = lastPoint;
        if (syncPoint == null) return;
        Location syncLoc = new Location(player.getWorld(), syncPoint.x(), syncPoint.y(), syncPoint.z(), (float) syncPoint.yaw(), (float) syncPoint.pitch());
        player.teleport(syncLoc);
        cameraEntity.setPos(syncPoint.x(), syncPoint.y(), syncPoint.z());
        cameraEntity.setRot((float)syncPoint.yaw(), (float)syncPoint.pitch());
    }

    @Override
    public void run() {
        if (!player.isOnline()) {
            finished = true;
            schedule.cancel(false);
            return;
        }

        if(cameraEntity == null || !cameraMounted) {
            // wait for camera init
            return;
        }

        if(!points.hasNext()) {
            finished = true;
            schedule.cancel(false);
            return;
        }

        lastPoint = points.next();

        ServerGamePacketListenerImpl pktHandler = (ServerGamePacketListenerImpl) ((CraftPlayer) player).getHandle().networkManager.getPacketListener();
        int awaitingTeleport = Reflect.on(pktHandler).get(REFLECTION_REMAPPER.remapFieldName(ServerGamePacketListenerImpl.class, "awaitingTeleport"));

        Reflect.on(pktHandler).set(REFLECTION_REMAPPER.remapFieldName(ServerGamePacketListenerImpl.class, "awaitingTeleport"), ++awaitingTeleport);

        FriendlyByteBuf msg = new FriendlyByteBuf(Unpooled.buffer());

        msg.writeVarInt(cameraEntity.getId());
        msg.writeDouble(lastPoint.x());
        msg.writeDouble(lastPoint.y());
        msg.writeDouble(lastPoint.z());
        msg.writeByte((byte)((int)(lastPoint.yaw() * 256.0F / 360.0F)));
        msg.writeByte((byte)((int)(lastPoint.pitch() * 256.0F / 360.0F)));
        msg.writeBoolean(false); // on ground

        ((CraftPlayer) player).getHandle().networkManager.send(new ClientboundTeleportEntityPacket(msg));
    }
}
