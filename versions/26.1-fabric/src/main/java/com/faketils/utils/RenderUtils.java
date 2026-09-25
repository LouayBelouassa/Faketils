package com.faketils.utils;

import com.faketils.events.FlyHandler;
import com.faketils.events.FtEvent;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.List;

public class RenderUtils {

    public static void renderWaypointMarker(
            Vec3 waypointPos,
            Vec3 cameraPos,
            int color,
            String waypointName,
            FtEvent.WorldRender event
    ) {
        float red   = ((color >> 16) & 0xFF) / 255f;
        float green = ((color >>  8) & 0xFF) / 255f;
        float blue  =  (color        & 0xFF) / 255f;
        float alpha = 0.5f;

        Vec3 rel = waypointPos.subtract(cameraPos);

        Matrix4f baseMatrix = new Matrix4f(event.positionMatrix)
                .translate((float) rel.x, (float) rel.y, (float) rel.z);


        Tesselator tesselator = Tesselator.getInstance();

        float cubeAlpha = 0.8f;
        float half = 0.5f;

        BufferBuilder cube = tesselator.begin(
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_COLOR
        );

        addCube(cube, baseMatrix, half, red, green, blue, cubeAlpha);

        RenderTypes.debugFilledBox().draw(cube.buildOrThrow());

        Matrix4f beamMatrix = new Matrix4f(baseMatrix)
                .translate(0f, 0.5f, 0f)
                .scale(0.2f, 50f, 0.2f);

        BufferBuilder beam = tesselator.begin(
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_COLOR
        );

        addCube(beam, beamMatrix, 0.5f, red, green, blue, alpha);
        RenderTypes.debugFilledBox().draw(beam.buildOrThrow());

        if (waypointName != null && !waypointName.isEmpty()) {
            renderWaypointName(
                    waypointPos,
                    cameraPos,
                    waypointName,
                    color,
                    event
            );
        }
    }

    public static void renderCurrentPath(Vec3 cameraPos, FtEvent.WorldRender event) {
        List<Vec3> currentPath = null;

        if (FlyHandler.path != null && !FlyHandler.path.isEmpty()) {
            currentPath = FlyHandler.path;
        }

        if (currentPath == null) return;

        for (int i = 0; i < currentPath.size(); i++) {
            Vec3 node = currentPath.get(i).add(0, -0.5, 0);

            int color = (i == 0) ? 0x00FF00 :
                    (i == currentPath.size() - 1) ? 0xFF0000 : 0xFFFF00;

            String name = (i == 0) ? "START" :
                    (i == currentPath.size() - 1) ? "GOAL" : "";

            renderWaypointMarker(node, cameraPos, color, name, event);
        }
    }

    private static void renderWaypointName(
            Vec3 waypointPos,
            Vec3 cameraPos,
            String name,
            int color,
            FtEvent.WorldRender event
    ) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        double distance = waypointPos.distanceTo(cameraPos);
        Vec3 rel = waypointPos.subtract(cameraPos).add(0, 2.0, 0);

        float scale = (float) Math.max(0.02, Math.min(0.1, distance * 0.05));

        float yaw = event.camera.yRot();
        float pitch = event.camera.xRot();

        Quaternionf rotation = new Quaternionf()
                .rotateY((float) Math.toRadians(-yaw))
                .rotateX((float) Math.toRadians(pitch));

        Matrix4f textMatrix = new Matrix4f(event.positionMatrix)
                .translate((float) rel.x, (float) rel.y, (float) rel.z)
                .rotate(rotation)
                .scale(-scale, -scale, scale);

        Component text = Component.literal(name);
        float x = -font.width(text) / 2f;

        MultiBufferSource.BufferSource provider = mc.renderBuffers().bufferSource();

        font.drawInBatch(
                text,
                x,
                0,
                color,
                true,
                textMatrix,
                provider,
                Font.DisplayMode.SEE_THROUGH,
                0,
                15728880
        );

        provider.endBatch();
    }

    public static void renderLine(
            Vec3 start,
            Vec3 end,
            int color,
            float width,
            FtEvent.WorldRender event
    ) {
        float red   = ((color >> 16) & 0xFF) / 255f;
        float green = ((color >>  8) & 0xFF) / 255f;
        float blue  =  (color        & 0xFF) / 255f;
        float alpha = ((color >> 24) & 0xFF) / 255f;

        Vec3 cam = event.camera.position();

        Vec3 startRel = start.subtract(cam);
        Vec3 endRel = end.subtract(cam);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(
                VertexFormat.Mode.LINES,
                DefaultVertexFormat.POSITION_COLOR
        );

        Matrix4f startMatrix = new Matrix4f(event.positionMatrix)
                .translate((float) startRel.x, (float) startRel.y, (float) startRel.z);

        Matrix4f endMatrix = new Matrix4f(event.positionMatrix)
                .translate((float) endRel.x, (float) endRel.y, (float) endRel.z);


        buffer.addVertex(startMatrix, (float) start.x, (float) start.y, (float) start.z)
                .setColor(red, green, blue, alpha);
        buffer.addVertex(endMatrix, (float) end.x, (float) end.y, (float) end.z)
                .setColor(red, green, blue, alpha);

        RenderTypes.lines().draw(buffer.buildOrThrow());
    }

    public static void renderLineToEntity(
            Entity entity,
            int color,
            float width,
            FtEvent.WorldRender event
    ) {
        if (entity == null || !entity.isAlive()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Vec3 eye = mc.player.getEyePosition();

        Vec3 target = entity.getPosition(event.tickDelta);

        renderLine(eye, target, color, width, event);
    }


    private static void addCube(
            BufferBuilder buffer,
            Matrix4f m,
            float h,
            float r,
            float g,
            float b,
            float a
    ) {
        // Top
        quad(buffer, m, -h,1,-h,  h,1,-h,  h,1,h, -h,1,h, r,g,b,a);
        // Bottom
        quad(buffer, m, -h,0,h,  h,0,h,  h,0,-h, -h,0,-h, r,g,b,a);
        // North
        quad(buffer, m, -h,0,-h,  h,0,-h,  h,1,-h, -h,1,-h, r,g,b,a);
        // South
        quad(buffer, m, -h,1,h,  h,1,h,  h,0,h, -h,0,h, r,g,b,a);
        // West
        quad(buffer, m, -h,0,h, -h,0,-h, -h,1,-h, -h,1,h, r,g,b,a);
        // East
        quad(buffer, m, h,1,h,  h,1,-h,  h,0,-h,  h,0,h, r,g,b,a);
    }

    private static void quad(
            BufferBuilder b,
            Matrix4f m,
            float x1,float y1,float z1,
            float x2,float y2,float z2,
            float x3,float y3,float z3,
            float x4,float y4,float z4,
            float r,float g,float bl,float a
    ) {
        b.addVertex(m,x1,y1,z1).setColor(r,g,bl,a);
        b.addVertex(m,x2,y2,z2).setColor(r,g,bl,a);
        b.addVertex(m,x3,y3,z3).setColor(r,g,bl,a);
        b.addVertex(m,x4,y4,z4).setColor(r,g,bl,a);
    }
}
