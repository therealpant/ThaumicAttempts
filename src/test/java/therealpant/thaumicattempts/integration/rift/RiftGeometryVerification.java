package therealpant.thaumicattempts.integration.rift;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;
import therealpant.thaumicattempts.core.ThaumicAttemptsTransformer;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.jar.JarFile;

/** Standalone checks; run using tools/verify-rift.gradle, without starting Minecraft. */
public final class RiftGeometryVerification {
    private static double totalLength140;
    private static double totalRise140;
    private static double totalPitch140;
    private static double totalTails140;
    public static void main(String[] args) throws Exception {
        int count = 0;
        for (int size : new int[]{1, 5, 15, 29, 30, 50, 100, 139, 140, 190, 200}) {
            for (int seed = 0; seed < 500; seed++) {
                verifyGeometry(size, seed);
                count++;
            }
        }
        System.out.println("Verified " + count + " noisy single turns: lengths, widths, bounds, determinism, irregular branches.");
        System.out.println("Size 140: average length=" + totalLength140 / 500.0D
                + ", rise=" + totalRise140 / 500.0D
                + ", pitch=" + totalPitch140 / 500.0D + ", axial tails=" + totalTails140 / 500.0D);
        checkPatch("thaumcraft/common/entities/EntityFluxRift", false, null);
        checkPatch("thaumcraft/client/renderers/entity/RenderFluxRift", true, null);
        checkPatch("thecodex6824/thaumicaugmentation/client/renderer/entity/RenderFluxRiftOptimized", true, null);
        for (String jar : args) {
            try (JarFile input = new JarFile(jar)) {
                if (input.getJarEntry("thaumcraft/common/entities/EntityFluxRift.class") != null) {
                    checkPatch("thaumcraft/common/entities/EntityFluxRift", false, input);
                    checkPatch("thaumcraft/client/renderers/entity/RenderFluxRift", true, input);
                } else {
                    checkPatch("thecodex6824/thaumicaugmentation/client/renderer/entity/RenderFluxRiftOptimized", true, input);
                }
            }
        }
    }

    private static void verifyGeometry(int size, int seed) {
        Random random = new Random(seed);
        ArrayList<double[]> points = new ArrayList<>();
        ArrayList<Double> radii = new ArrayList<>();
        double[] right = normalize(new double[]{random.nextGaussian(), random.nextGaussian(), random.nextGaussian()});
        double[] left = scale(right, -1.0D);
        double[] pr = new double[3], pl = new double[3];
        int steps = (int) Math.ceil(size / 3.0F);
        float girth = size / 300.0F, decrement = girth / steps;
        // TC6 calcSteps' two seeded random walks, including the half-length end caps.
        for (int i = 0; i < steps; i++) {
            girth -= decrement;
            right = rotate(right, random);
            pr = add(pr, scale(right, 0.2D));
            points.add(pr); radii.add((double) girth);
            left = rotate(left, random);
            pl = add(pl, scale(left, 0.2D));
            points.add(0, pl); radii.add(0, (double) girth);
        }
        points.add(add(pr, scale(right, 0.1D))); radii.add(0.0D);
        points.add(0, add(pl, scale(left, 0.1D))); radii.add(0, 0.0D);
        double[][] original = points.toArray(new double[0][]);
        double[] widths = new double[radii.size()];
        for (int i = 0; i < widths.length; i++) widths[i] = radii.get(i);
        double[] widthCopy = widths.clone();
        double radius = RiftWellGeometry.CYLINDER_RADIUS;
        double halfHeight = RiftWellGeometry.CYLINDER_HALF_HEIGHT;
        double[][] bent = RiftWellGeometry.bend(original, widths, radius, halfHeight, seed);
        double[][] guide = RiftWellGeometry.bendGuide(original, widths, radius, halfHeight, seed);
        require(bent.length == original.length, "point count");
        require(Arrays.equals(widths, widthCopy), "vanilla widths modified");
        double[][] again = RiftWellGeometry.bend(original, widths, radius, halfHeight, seed);
        double[][] reapplied = RiftWellGeometry.bend(bent, widths, radius, halfHeight, seed);
        double upperBound = RiftWellGeometry.upperBound(bent, widths, halfHeight);
        if (size <= 140) require(upperBound <= halfHeight + 2.5D, "turn exceeds its additional headroom");
        for (int tip : new int[]{0, 1, bent.length - 2, bent.length - 1}) {
            require(guide[tip][0] == 0.0D && guide[tip][2] == 0.0D, "guide tip is off the vertical axis");
        }
        double minBodyRadius = Double.MAX_VALUE, maxBodyRadius = 0.0D;
        double lastTurn = 0.0D;
        double winding = 0.0D;
        int bends = 0;
        double totalDeviation = 0.0D;
        int firstTurnIndex = -1, lastTurnIndex = -1;
        for (int i = 0; i < bent.length; i++) {
            double deviation = distance(bent[i], guide[i]);
            require(deviation > 1.0E-7D && deviation <= RiftWellGeometry.MAX_NODE_DEVIATION,
                    "missing or excessive node noise: size=" + size + ", seed=" + seed + ", node=" + i);
            totalDeviation += deviation;
            require(Arrays.equals(bent[i], again[i]), "non-deterministic shape");
            require(distance(bent[i], reapplied[i]) < 1.0E-8D,
                    "reapplying shape: size=" + size + ", seed=" + seed + ", point=" + i
                            + ", displacement=" + distance(bent[i], reapplied[i]));
            double halo = Math.max(0.0D, widths[i]) * 2.25D * 1.15D;
            double radial = Math.hypot(bent[i][0], bent[i][2]) + halo + Math.sqrt(2.0D) * 0.15D;
            require(radial <= radius + 1.0E-8D, "horizontal bounds: size=" + size + ", seed=" + seed);
            require(bent[i][1] - halo - 0.15D >= -halfHeight - 1.0E-8D
                            && bent[i][1] + halo + 0.15D <= upperBound + 1.0E-8D,
                    "vertical bounds: size=" + size + ", seed=" + seed + ", point=" + i + ", y=" + bent[i][1]);
            if (i > bent.length / 3 && i < bent.length * 2 / 3) {
                double bodyRadius = Math.hypot(bent[i][0], bent[i][2]);
                minBodyRadius = Math.min(minBodyRadius, bodyRadius);
                maxBodyRadius = Math.max(maxBodyRadius, bodyRadius);
            }
            if (i > 0) {
                require(guide[i][1] > guide[i - 1][1], "guide does not rise monotonically");
                double cross = guide[i - 1][0] * guide[i][2] - guide[i - 1][2] * guide[i][0];
                double dot = guide[i - 1][0] * guide[i][0] + guide[i - 1][2] * guide[i][2];
                if (dot != 0.0D || cross != 0.0D) winding += Math.atan2(cross, dot);
                if (Math.abs(cross) > 1.0E-10D) {
                    if (firstTurnIndex < 0) firstTurnIndex = i - 1;
                    lastTurnIndex = i;
                    require(lastTurn == 0.0D || cross * lastTurn > 0.0D, "spiral reverses winding");
                    lastTurn = cross;
                }
                if (size == 140) totalLength140 += distance(original[i], original[i - 1]);
            }
            if (i > 0) require(Math.abs(distance(bent[i], bent[i - 1])
                    - distance(original[i], original[i - 1])) < 1.0E-8D, "segment length");
            if (i > 0 && i < bent.length - 1) {
                double[] before = normalize(add(guide[i], scale(guide[i - 1], -1.0D)));
                double[] after = normalize(add(guide[i + 1], scale(guide[i], -1.0D)));
                if (distance(before, after) > 0.05D) bends++;
            }
        }
        if (bent.length >= 12) {
            require(Math.abs(Math.abs(winding) - Math.PI * 2.0D) < 1.0E-8D, "not exactly one turn");
            require(bends >= 6 && bends <= 9, "missing polygonal corners: " + bends);
        }
        if (size == 140) {
            double pitch = guide[lastTurnIndex][1] - guide[firstTurnIndex][1];
            int bottom = 0, top = guide.length - 1;
            while (guide[bottom + 1][0] == 0.0D && guide[bottom + 1][2] == 0.0D) bottom++;
            while (guide[top - 1][0] == 0.0D && guide[top - 1][2] == 0.0D) top--;
            double lowerTail = guide[bottom][1] - guide[0][1];
            double upperTail = guide[guide.length - 1][1] - guide[top][1];
            require(pitch > 3.0D && pitch < 5.0D, "central pitch was not increased: " + pitch);
            require(lowerTail > 0.5D && lowerTail < 3.3D && upperTail > 0.5D && upperTail < 3.3D,
                    "vertical tails were not shortened: " + lowerTail + ", " + upperTail);
            totalPitch140 += pitch;
            totalTails140 += lowerTail + upperTail;
            totalRise140 += bent[bent.length - 1][1] - bent[0][1];
            require(maxBodyRadius - minBodyRadius > 0.02D, "body lacks irregularity");
            require(guide[10][0] == 0.0D && guide[10][2] == 0.0D, "lower guide branch is not vertical");
            require(guide[guide.length - 11][0] == 0.0D && guide[guide.length - 11][2] == 0.0D,
                    "upper guide branch is not vertical");
            require(totalDeviation / bent.length > 0.015D, "noise is too faint");
            for (int i : new int[]{5, 10, bent.length - 11, bent.length - 6}) {
                double[] before = normalize(add(bent[i], scale(bent[i - 1], -1.0D)));
                double[] after = normalize(add(bent[i + 1], scale(bent[i], -1.0D)));
                require(distance(before, after) > 0.001D, "branch remains perfectly straight");
            }
        }
    }

    private static void checkPatch(String name, boolean renderer, JarFile jar) throws Exception {
        byte[] bytes;
        try (InputStream input = jar == null
                ? RiftGeometryVerification.class.getClassLoader().getResourceAsStream(name + ".class")
                : jar.getInputStream(jar.getJarEntry(name + ".class"))) {
            require(input != null, "missing class " + name);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
            bytes = output.toByteArray();
        }
        Method patch = ThaumicAttemptsTransformer.class.getDeclaredMethod("patchRiftGeometry", byte[].class, boolean.class);
        patch.setAccessible(true);
        byte[] transformed = (byte[]) patch.invoke(new ThaumicAttemptsTransformer(), bytes, renderer);
        ClassNode node = new ClassNode();
        new ClassReader(transformed).accept(node, 0);
        int hooks = 0;
        int stationary = 0;
        int lifecycle = 0;
        for (MethodNode method : node.methods) {
            boolean hooked = false;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction instanceof MethodInsnNode
                        && ((MethodInsnNode) instruction).owner.endsWith("/RiftWellLifecycle")) {
                    lifecycle++; hooked = true;
                }
                if (instruction instanceof MethodInsnNode
                        && ((MethodInsnNode) instruction).owner.endsWith("/RiftWellShapeHooks")) {
                    hooks++; hooked = true;
                    if ("renderStability".equals(((MethodInsnNode) instruction).name)) stationary++;
                }
            }
            if (hooked) new Analyzer<>(new BasicVerifier()).analyze(name, method);
        }
        require(hooks > 0, "missing injected hooks");
        require(!renderer || stationary > 0, "missing stationary-render hook");
        require(renderer || lifecycle == 1, "missing or duplicated lifecycle hook");
        System.out.println("Verified bytecode hooks: " + name + (jar == null ? " (mapped)" : " (production)"));
    }

    private static double[] rotate(double[] v, Random random) {
        double pitch = (float) (random.nextGaussian() * 0.33D);
        double yaw = (float) (random.nextGaussian() * 0.33D);
        double py = v[1] * Math.cos(pitch) + v[2] * Math.sin(pitch);
        double pz = v[2] * Math.cos(pitch) - v[1] * Math.sin(pitch);
        return new double[]{v[0] * Math.cos(yaw) + pz * Math.sin(yaw), py,
                pz * Math.cos(yaw) - v[0] * Math.sin(yaw)};
    }

    private static double[] normalize(double[] v) { return scale(v, 1.0D / distance(v, new double[3])); }
    private static double[] scale(double[] v, double s) { return new double[]{v[0] * s, v[1] * s, v[2] * s}; }
    private static double[] add(double[] a, double[] b) { return new double[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]}; }
    private static double distance(double[] a, double[] b) {
        double x = a[0] - b[0], y = a[1] - b[1], z = a[2] - b[2];
        return Math.sqrt(x * x + y * y + z * z);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
