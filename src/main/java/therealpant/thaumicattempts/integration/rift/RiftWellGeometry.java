package therealpant.thaumicattempts.integration.rift;

import java.util.Random;

/** One stationary polygonal turn with seeded, naturally uneven axial branches. */
public strictfp final class RiftWellGeometry {
    public static final double CYLINDER_RADIUS = 3.0D;
    public static final double CYLINDER_HALF_HEIGHT = 5.0D;
    private static final double HALO_SCALE = 2.25D * 1.15D;
    private static final double RADIAL_WOBBLE = StrictMath.sqrt(2.0D) * 0.15D;
    private static final int SIDES = 6;
    private static final double LOOP_LENGTH_SHARE = 0.65D;
    private static final double LOOP_PITCH_STRETCH = 1.08D;
    private static final double NOISE_CLEARANCE = 0.10D;
    static final double MAX_NODE_DEVIATION = 0.24D;

    private RiftWellGeometry() {}

    public static double[][] bend(double[][] original, double[] widths,
                                  double radius, double halfHeight, int seed) {
        double[][] guide = bendGuide(original, widths, radius, halfHeight, seed);
        if (original.length < 2) return guide;
        double[] lengths = new double[original.length - 1];
        double total = 0.0D;
        for (int i = 0; i < lengths.length; i++) total += lengths[i] = length(original[i], original[i + 1]);
        if (total < 1.0E-10D) return guide;
        double[][] offsets = new double[original.length][3];
        int middle = original.length / 2;
        for (int i = 0; i < offsets.length; i++) {
            // Anchor noise to the seed and distance in nodes from the centre, not
            // the clock or the previously bent coordinates. Coarse bends plus
            // a smaller per-node disturbance avoid both straight rods and a sawtooth.
            int node = i - middle;
            int cell = Math.floorDiv(node, 4);
            double t = Math.floorMod(node, 4) / 4.0D;
            t = t * t * (3.0D - 2.0D * t);
            for (int axis = 0; axis < 3; axis++) {
                double coarse = noise(seed, cell, axis) * (1.0D - t) + noise(seed, cell + 1, axis) * t;
                offsets[i][axis] = (0.12D * coarse + 0.018D * noise(seed, node, axis + 3))
                        * Math.min(1.0D, total / 3.0D);
            }
        }
        double top = upperBound(guide, widths, halfHeight);
        if (top > halfHeight) top += MAX_NODE_DEVIATION;
        double strength = 1.0D;
        for (int attempt = 0; attempt < 16; attempt++, strength *= 0.5D) {
            double[][] result = new double[guide.length][3];
            for (int axis = 0; axis < 3; axis++) result[middle][axis] = guide[middle][axis] + offsets[middle][axis] * strength;
            // Work outwards from the centre. Project each next target onto its
            // native-length segment, so adding irregularity never inflates SIZE.
            for (int i = middle + 1; i < result.length; i++) {
                noisySegment(result, guide, offsets, i, i - 1, lengths[i - 1], strength);
            }
            for (int i = middle - 1; i >= 0; i--) {
                noisySegment(result, guide, offsets, i, i + 1, lengths[i], strength);
            }
            if (fitVerticalNoise(result, widths, halfHeight, top)
                    && fitsNoise(result, guide, widths, radius, halfHeight, top)) return result;
        }
        return guide;
    }

    private static double noise(int seed, int node, int axis) {
        return new Random(((long) seed * 0x9E3779B97F4A7C15L)
                ^ ((long) node * 0xD1B54A32D192ED03L) ^ ((long) (axis + 1) * 0x94D049BB133111EBL))
                .nextDouble() * 2.0D - 1.0D;
    }

    private static void noisySegment(double[][] result, double[][] guide, double[][] offsets,
                                      int index, int previous, double span, double strength) {
        double[] delta = new double[3];
        double squared = 0.0D;
        for (int axis = 0; axis < 3; axis++) {
            delta[axis] = guide[index][axis] + offsets[index][axis] * strength - result[previous][axis];
            squared += delta[axis] * delta[axis];
        }
        if (squared < 1.0E-20D) {
            squared = 0.0D;
            for (int axis = 0; axis < 3; axis++) {
                delta[axis] = guide[index][axis] - guide[previous][axis];
                squared += delta[axis] * delta[axis];
            }
        }
        double scale = squared > 0.0D ? span / StrictMath.sqrt(squared) : 0.0D;
        for (int axis = 0; axis < 3; axis++) result[index][axis] = result[previous][axis] + delta[axis] * scale;
    }

    private static boolean fitsNoise(double[][] result, double[][] guide, double[] widths,
                                      double radius, double halfHeight, double top) {
        for (int i = 0; i < result.length; i++) {
            double[] p = result[i];
            double halo = Math.max(0.0D, widths[i]) * HALO_SCALE;
            if (length(p, guide[i]) > MAX_NODE_DEVIATION
                    || StrictMath.hypot(p[0], p[2]) + halo + RADIAL_WOBBLE > radius
                    || p[1] - halo - 0.15D < -halfHeight || p[1] + halo + 0.15D > top) return false;
        }
        return true;
    }

    private static boolean fitVerticalNoise(double[][] points, double[] widths, double halfHeight, double top) {
        double low = Double.NEGATIVE_INFINITY, high = Double.POSITIVE_INFINITY;
        for (int i = 0; i < points.length; i++) {
            double margin = Math.max(0.0D, widths[i]) * HALO_SCALE + 0.17D;
            low = Math.max(low, -halfHeight + margin - points[i][1]);
            high = Math.min(high, top - margin - points[i][1]);
        }
        if (low > high) return false;
        // A taller turn rests near the floor. Move the whole noisy curve slightly
        // up if needed instead of suppressing every node's noise because of one tip.
        double shift = Math.max(low, Math.min(0.0D, high));
        for (double[] point : points) point[1] += shift;
        return true;
    }

    /** Unperturbed skeleton, also used to verify preservation of the overall silhouette. */
    static double[][] bendGuide(double[][] original, double[] widths,
                                 double radius, double halfHeight, int seed) {
        int count = original.length;
        double[][] result = new double[count][3];
        if (count < 2) return result;
        double[] distance = new double[count];
        double maxWidth = 0.0D;
        for (double width : widths) maxWidth = Math.max(maxWidth, width);
        for (int i = 1; i < count; i++) distance[i] = distance[i - 1] + length(original[i - 1], original[i]);
        double total = distance[count - 1];
        double horizontal = Math.max(0.01D, radius - maxWidth * HALO_SCALE - RADIAL_WOBBLE - 0.02D - NOISE_CLEARANCE);
        if (count < SIDES + 6 || total < 1.0E-10D) {
            // A newborn rift has too few native points for a turn and two tails.
            for (int i = 0; i < count; i++) result[i][1] = distance[i];
        } else {
            Random random = new Random(seed);
            double phase = random.nextDouble() * StrictMath.PI * 2.0D;
            double handedness = random.nextBoolean() ? 1.0D : -1.0D;
            // Spend more of the native arc length on the turn, taking it equally
            // from both axial tails. Its radius stays bounded, so the extra length
            // becomes pitch rather than another revolution or a wider coil.
            double loopLength = Math.min(total * LOOP_LENGTH_SHARE, SIDES * horizontal * LOOP_PITCH_STRETCH);
            int[] corners = new int[SIDES + 1];
            corners[0] = nearest(distance, (total - loopLength) * 0.5D, 2, count - SIDES - 3);
            corners[SIDES] = nearest(distance, (total + loopLength) * 0.5D, corners[0] + SIDES, count - 3);
            double[] weights = new double[SIDES];
            double sum = 0.0D;
            for (int i = 0; i < SIDES; i++) sum += weights[i] = 0.85D + random.nextDouble() * 0.3D;
            double accumulated = 0.0D;
            for (int i = 1; i < SIDES; i++) {
                accumulated += weights[i - 1];
                corners[i] = nearest(distance, distance[corners[0]]
                        + (distance[corners[SIDES]] - distance[corners[0]]) * accumulated / sum,
                        corners[i - 1] + 1, corners[SIDES] - (SIDES - i));
            }
            double[] edges = new double[SIDES];
            double low = 0.0D, high = total;
            for (int i = 0; i < SIDES; i++) {
                edges[i] = distance[corners[i + 1]] - distance[corners[i]];
                low = Math.max(low, edges[i] * 0.5D);
            }
            // Unequal polygon edges give unequal angles, but exactly ONE revolution.
            for (int iteration = 0; iteration < 48; iteration++) {
                double candidate = (low + high) * 0.5D;
                if (angleSum(edges, candidate) > StrictMath.PI * 2.0D) low = candidate;
                else high = candidate;
            }
            double planarRadius = high;
            double coilRadius = Math.min(horizontal, planarRadius * 0.995D);
            coilRadius = Math.min(coilRadius, Math.min(distance[corners[0]] - distance[1],
                    distance[count - 2] - distance[corners[SIDES]]) * 0.98D);
            int bottom = corners[0] - 1;
            while (bottom > 1 && distance[corners[0]] - distance[bottom] < coilRadius * 1.005D) bottom--;
            int top = corners[SIDES] + 1;
            while (top < count - 2 && distance[top] - distance[corners[SIDES]] < coilRadius * 1.005D) top++;
            for (int i = 0; i <= bottom; i++) result[i][1] = distance[i];
            double x = coilRadius * StrictMath.cos(phase), z = coilRadius * StrictMath.sin(phase);
            line(result, distance, bottom, corners[0], x, z);
            double angle = phase;
            for (int i = 0; i < SIDES; i++) {
                angle += handedness * 2.0D * StrictMath.asin(Math.min(1.0D, edges[i] / (2.0D * planarRadius)));
                // Close XZ exactly; the Y difference keeps it a helix.
                line(result, distance, corners[i], corners[i + 1],
                        i == SIDES - 1 ? x : coilRadius * StrictMath.cos(angle),
                        i == SIDES - 1 ? z : coilRadius * StrictMath.sin(angle));
            }
            line(result, distance, corners[SIDES], top, 0.0D, 0.0D);
            for (int i = top + 1; i < count; i++) result[i][1] = result[top][1] + distance[i] - distance[top];
        }
        double centre = result[count - 1][1] * 0.5D;
        // Preserve clearance above the liquid when a taller turn needs extra headroom.
        double floor = -halfHeight;
        for (int i = 0; i < count; i++) floor = Math.max(floor,
                -halfHeight + Math.max(0.0D, widths[i]) * HALO_SCALE + 0.17D - result[i][1]);
        double offset = Math.max(-centre, floor);
        for (double[] point : result) point[1] += offset;
        return result;
    }

    /** Taller turns extend upward without moving the liquid-side floor. */
    public static double upperBound(double[][] points, double[] widths, double halfHeight) {
        double top = halfHeight;
        for (int i = 0; i < points.length; i++) {
            top = Math.max(top, points[i][1] + Math.max(0.0D, widths[i]) * HALO_SCALE + 0.17D);
        }
        return top;
    }

    private static void line(double[][] points, double[] distance, int start, int end, double x, double z) {
        double dx = x - points[start][0], dz = z - points[start][2];
        double span = distance[end] - distance[start];
        double rise = StrictMath.sqrt(Math.max(0.0D, span * span - dx * dx - dz * dz));
        for (int i = start + 1; i <= end; i++) {
            double t = (distance[i] - distance[start]) / span;
            points[i][0] = points[start][0] + dx * t;
            points[i][1] = points[start][1] + rise * t;
            points[i][2] = points[start][2] + dz * t;
        }
        points[end][0] = x;
        points[end][2] = z;
    }

    private static double angleSum(double[] edges, double radius) {
        double sum = 0.0D;
        for (double edge : edges) sum += 2.0D * StrictMath.asin(Math.min(1.0D, edge / (2.0D * radius)));
        return sum;
    }

    private static int nearest(double[] distance, double target, int low, int high) {
        int best = low;
        for (int i = low + 1; i <= high; i++) {
            if (Math.abs(distance[i] - target) < Math.abs(distance[best] - target)) best = i;
        }
        return best;
    }

    private static double length(double[] a, double[] b) {
        double x = a[0] - b[0], y = a[1] - b[1], z = a[2] - b[2];
        return StrictMath.sqrt(x * x + y * y + z * z);
    }
}
