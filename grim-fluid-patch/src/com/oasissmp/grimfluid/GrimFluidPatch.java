package com.oasissmp.grimfluid;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Copies the GrimAC jar with its bundled packetevents counting bubble columns
 * as water, so Grim stops setting back players riding one.
 *
 * <p>For 26.1+ clients, packetevents keeps a fluid count per chunk section,
 * moving it by {@code WrappedBlockState.isFluid} as blocks change, and Grim
 * predicts a section whose count is 0 as having no water. {@code isFluid}
 * misses the blocks that always hold water without a waterlogged property, so
 * when soul sand or magma turns water into a bubble column the count drops, and
 * a player in a column is predicted as if in air: every tick Grim's Simulation
 * check flags and sets them back. This makes {@code isFluid} true for those
 * blocks first, as packetevents' own fix does
 * (https://github.com/retrooper/packetevents/pull/1616). Grim's report is
 * https://github.com/GrimAnticheat/Grim/issues/2726.
 *
 * <p>The class's sha256 is pinned: a different GrimAC fails here, to be checked
 * for whether it bundles the fix, and the patch dropped or pinned again.
 *
 * <p>Usage: {@code GrimFluidPatch <GrimAC.jar> <patched GrimAC.jar>}
 */
public final class GrimFluidPatch {

    private static final String PACKETEVENTS = "ac/grim/grimac/shaded/com/github/retrooper/packetevents/";
    private static final String BLOCK_STATE = PACKETEVENTS + "protocol/world/states/WrappedBlockState";
    private static final String STATE_TYPES = PACKETEVENTS + "protocol/world/states/type/StateTypes";
    private static final String STATE_TYPE = PACKETEVENTS + "protocol/world/states/type/StateType";
    private static final String CLASS_ENTRY = BLOCK_STATE + ".class";
    // The class in the GrimAC artifacts.lock pins, checked to lack the fix.
    private static final String CLASS_SHA256 = "a6f9793c33486398f652f6dca28ad61d25f01386a500b8b105cef47d35dd8532";
    private static final List<String> WATER_HOLDING = List.of(
            "BUBBLE_COLUMN", "KELP", "KELP_PLANT", "SEAGRASS", "TALL_SEAGRASS");

    private static final ClassDesc BLOCK_STATE_DESC = ClassDesc.ofInternalName(BLOCK_STATE);
    private static final ClassDesc STATE_TYPES_DESC = ClassDesc.ofInternalName(STATE_TYPES);
    private static final ClassDesc STATE_TYPE_DESC = ClassDesc.ofInternalName(STATE_TYPE);

    private GrimFluidPatch() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("usage: GrimFluidPatch <GrimAC.jar> <patched GrimAC.jar>");
            System.exit(2);
        }
        try (ZipFile jar = new ZipFile(args[0])) {
            ZipEntry target = jar.getEntry(CLASS_ENTRY);
            if (target == null) {
                fail(args[0] + " has no " + CLASS_ENTRY);
            }
            byte[] original = read(jar, target);
            String digest = sha256(original);
            if (!digest.equals(CLASS_SHA256)) {
                fail(CLASS_ENTRY + " has sha256 " + digest + ", not the " + CLASS_SHA256
                        + " this patch was written for");
            }
            byte[] patched = patch(jar, original);
            try (OutputStream file = Files.newOutputStream(Path.of(args[1]));
                    ZipOutputStream out = new ZipOutputStream(file)) {
                for (ZipEntry entry : jar.stream().toList()) {
                    boolean isTarget = entry.getName().equals(CLASS_ENTRY);
                    // The patched class has a new size and CRC; every other
                    // entry keeps its own, and is recompressed.
                    ZipEntry copy = isTarget ? new ZipEntry(entry.getName()) : new ZipEntry(entry);
                    copy.setTime(entry.getTime());
                    copy.setCompressedSize(-1);
                    out.putNextEntry(copy);
                    out.write(isTarget ? patched : read(jar, entry));
                    out.closeEntry();
                }
            }
        }
        System.out.println("GrimAC's packetevents counts bubble columns, kelp and seagrass as water");
    }

    /**
     * Puts {@code if (getType() is one of WATER_HOLDING) return true;} ahead of
     * {@code isFluid}'s own code. Stack maps are regenerated, which can need
     * the hierarchy of the jar's own classes, so they are read from the jar.
     */
    static byte[] patch(ZipFile jar, byte[] classfile) {
        ClassFile classFile = ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(
                ClassHierarchyResolver.ofResourceParsing((ClassDesc desc) -> openClass(jar, desc))
                        .orElse(ClassHierarchyResolver.defaultResolver())));
        ClassModel model = classFile.parse(classfile);
        Predicate<MethodModel> isFluid = m -> m.methodName().equalsString("isFluid")
                && m.methodTypeSymbol().descriptorString().equals("()Z");
        long matches = model.methods().stream().filter(isFluid).count();
        if (matches != 1) {
            fail("expected one isFluid()Z in " + BLOCK_STATE + ", found " + matches);
        }
        return classFile.transformClass(model,
                ClassTransform.transformingMethodBodies(isFluid, new HoldsWaterFirst()));
    }

    private static final class HoldsWaterFirst implements CodeTransform {

        @Override
        public void atStart(CodeBuilder builder) {
            Label holdsWater = builder.newLabel();
            Label rest = builder.newLabel();
            builder.aload(0)
                    .invokevirtual(BLOCK_STATE_DESC, "getType", MethodTypeDesc.of(STATE_TYPE_DESC));
            for (String type : WATER_HOLDING) {
                builder.dup()
                        .getstatic(STATE_TYPES_DESC, type, STATE_TYPE_DESC)
                        .if_acmpeq(holdsWater);
            }
            builder.pop()
                    .goto_(rest)
                    .labelBinding(holdsWater)
                    .pop()
                    .iconst_1()
                    .ireturn()
                    .labelBinding(rest);
        }

        @Override
        public void accept(CodeBuilder builder, CodeElement element) {
            builder.with(element);
        }
    }

    private static InputStream openClass(ZipFile jar, ClassDesc desc) {
        String internalName = desc.descriptorString();
        ZipEntry entry = jar.getEntry(internalName.substring(1, internalName.length() - 1) + ".class");
        if (entry == null) {
            return null;
        }
        try {
            return new ByteArrayInputStream(read(jar, entry));
        } catch (IOException e) {
            throw new IllegalStateException("reading " + entry.getName(), e);
        }
    }

    private static byte[] read(ZipFile jar, ZipEntry entry) throws IOException {
        try (InputStream in = jar.getInputStream(entry)) {
            return in.readAllBytes();
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void fail(String message) {
        System.err.println("error: " + message);
        System.exit(1);
    }
}
