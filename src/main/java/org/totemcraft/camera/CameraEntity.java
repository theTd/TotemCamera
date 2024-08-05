package org.totemcraft.camera;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Slime;

public class CameraEntity extends Slime {
    CameraEntity(ServerLevel world, double x, double y, double z, float yaw, float pitch) {
        super(EntityType.SLIME, world);
        setPos(x,y,z);
        //System.out.println(x+" "+y+" "+z+" "+yaw+" "+pitch);
        setRot(yaw, pitch);
        setInvisible(true);
        setSize(1, false);
        setInvulnerable(true);
        goalSelector.removeAllGoals();
    }

    public void remove() {
        remove(RemovalReason.UNLOADED_WITH_PLAYER);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public void tick() {
    }
}
