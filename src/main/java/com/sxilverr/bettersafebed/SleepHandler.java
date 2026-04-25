package com.sxilverr.bettersafebed;

import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.pathfinding.Path;
import net.minecraft.pathfinding.PathNavigate;
import net.minecraft.pathfinding.PathPoint;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

@Mod.EventBusSubscriber(modid = BetterSafeBed.MODID)
public final class SleepHandler {

    private static final Field SLEEPING_FIELD;
    private static final Field SLEEP_TIMER_FIELD;
    private static final Field BED_LOCATION_FIELD;
    private static final Method UPDATE_SLEEPING_FLAG_METHOD;
    private static final Method SET_SIZE_METHOD;

    static {
        try {
            SLEEPING_FIELD = ReflectionHelper.findField(EntityPlayer.class, "sleeping", "field_71083_bS");
            SLEEP_TIMER_FIELD = ReflectionHelper.findField(EntityPlayer.class, "sleepTimer", "field_71076_b");
            BED_LOCATION_FIELD = ReflectionHelper.findField(EntityPlayer.class, "bedLocation", "field_71081_bT");
            UPDATE_SLEEPING_FLAG_METHOD = ReflectionHelper.findMethod(World.class, "updateAllPlayersSleepingFlag", "func_72854_c");
            UPDATE_SLEEPING_FLAG_METHOD.setAccessible(true);
            SET_SIZE_METHOD = ReflectionHelper.findMethod(net.minecraft.entity.Entity.class, "setSize", "func_70105_a", float.class, float.class);
            SET_SIZE_METHOD.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException("BetterSafeBed: failed to access sleep state via reflection", e);
        }
    }

    private SleepHandler() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlayerSleep(PlayerSleepInBedEvent event) {
        EntityPlayer player = event.getEntityPlayer();
        World world = player.world;
        BlockPos pos = event.getPos();

        if (world.isRemote) return;
        if (event.getResultStatus() != null) return;

        if (player.isPlayerSleeping() || !player.isEntityAlive()) {
            event.setResult(EntityPlayer.SleepResult.OTHER_PROBLEM);
            return;
        }
        if (!world.provider.isSurfaceWorld()) {
            event.setResult(EntityPlayer.SleepResult.NOT_POSSIBLE_HERE);
            return;
        }
        if (world.isDaytime()) {
            event.setResult(EntityPlayer.SleepResult.NOT_POSSIBLE_NOW);
            return;
        }
        if (Math.abs(player.posX - pos.getX()) > 3.0D
                || Math.abs(player.posY - pos.getY()) > 2.0D
                || Math.abs(player.posZ - pos.getZ()) > 3.0D) {
            event.setResult(EntityPlayer.SleepResult.TOO_FAR_AWAY);
            return;
        }

        AxisAlignedBB scanBox = new AxisAlignedBB(
                pos.getX() - 8.0D, pos.getY() - 5.0D, pos.getZ() - 8.0D,
                pos.getX() + 8.0D, pos.getY() + 5.0D, pos.getZ() + 8.0D);
        List<EntityMob> mobs = world.getEntitiesWithinAABB(EntityMob.class, scanBox);
        for (EntityMob mob : mobs) {
            if (mob.getAttackTarget() == player && canReach(mob, player)) {
                event.setResult(EntityPlayer.SleepResult.NOT_SAFE);
                return;
            }
        }

        if (player.isRiding()) player.dismountRidingEntity();
        try {
            SET_SIZE_METHOD.invoke(player, 0.2F, 0.2F);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        positionForSleep(player, world, pos);

        try {
            SLEEPING_FIELD.setBoolean(player, true);
            SLEEP_TIMER_FIELD.setInt(player, 0);
            BED_LOCATION_FIELD.set(player, pos);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        player.motionX = 0.0D;
        player.motionY = 0.0D;
        player.motionZ = 0.0D;

        try {
            UPDATE_SLEEPING_FLAG_METHOD.invoke(world);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }

        event.setResult(EntityPlayer.SleepResult.OK);
    }

    private static boolean canReach(EntityMob mob, EntityPlayer player) {
        PathNavigate navigator = mob.getNavigator();
        if (navigator == null) return true;
        Path path = navigator.getPathToEntityLiving(player);
        if (path == null) return false;
        PathPoint finalPoint = path.getFinalPathPoint();
        if (finalPoint == null) return false;
        double dx = (finalPoint.x + 0.5D) - player.posX;
        double dy = finalPoint.y - player.posY;
        double dz = (finalPoint.z + 0.5D) - player.posZ;
        return dx * dx + dy * dy + dz * dz <= 2.25D;
    }

    private static void positionForSleep(EntityPlayer player, World world, BlockPos pos) {
        if (world.isBlockLoaded(pos)) {
            IBlockState state = world.getBlockState(pos);
            if (state.getProperties().containsKey(BlockHorizontal.FACING)) {
                EnumFacing facing = state.getValue(BlockHorizontal.FACING);
                float xOff = 0.5F + facing.getFrontOffsetX() * 0.4F;
                float zOff = 0.5F + facing.getFrontOffsetZ() * 0.4F;
                player.setPosition(pos.getX() + xOff, pos.getY() + 0.6875F, pos.getZ() + zOff);
                return;
            }
        }
        player.setPosition(pos.getX() + 0.5D, pos.getY() + 0.6875D, pos.getZ() + 0.5D);
    }
}
