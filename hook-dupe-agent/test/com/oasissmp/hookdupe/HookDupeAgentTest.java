package com.oasissmp.hookdupe;

import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Instruction;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Runs {@link HookDupeAgent#patch} over stand-ins for {@code TripWireHookBlock}
 * generated here: it refuses one with no guard and one with two, and rewrites
 * the guard of one with exactly one. The real class has exactly one, so only
 * these show the refusals.
 */
public final class HookDupeAgentTest {

    private static final ClassDesc HOOK_BLOCK = desc(HookDupeAgent.TARGET_CLASS);
    private static final ClassDesc BLOCKS = desc(HookDupeAgent.BLOCKS);
    private static final ClassDesc BLOCK_STATE = desc(HookDupeAgent.BLOCK_STATE);
    private static final ClassDesc BLOCK = desc("net/minecraft/world/level/block/Block");
    private static final ClassDesc LEVEL = desc("net/minecraft/world/level/Level");
    private static final ClassDesc BLOCK_POS = desc("net/minecraft/core/BlockPos");
    private static final MethodTypeDesc IS = MethodTypeDesc.of(ConstantDescs.CD_boolean, BLOCK);
    private static final MethodTypeDesc GET_BLOCK_STATE = MethodTypeDesc.of(BLOCK_STATE, BLOCK_POS);
    private static final MethodTypeDesc ON_REMOVED =
            MethodTypeDesc.of(ConstantDescs.CD_void, BLOCK_STATE, LEVEL, BLOCK_POS);
    // calculateState(Level level, BlockPos pos, BlockState state), static.
    private static final MethodTypeDesc CALCULATE_STATE =
            MethodTypeDesc.of(ConstantDescs.CD_void, LEVEL, BLOCK_POS, BLOCK_STATE);

    private HookDupeAgentTest() {}

    public static void main(String[] args) {
        List<String> failures = new ArrayList<>();

        expectRefusal(failures, "no guard", hookBlock(code -> {
            removalAfterTest(code, "TRIPWIRE", true);
            // onRemoved followed by Paper's guard rather than a return.
            removalAfterTest(code, "TRIPWIRE_HOOK", false);
            paperGuard(code);
            code.return_();
        }));
        expectRefusal(failures, "two guards", hookBlock(code -> {
            mojangGuard(code);
            mojangGuard(code);
            code.return_();
        }));

        byte[] patched = HookDupeAgent.patch(HookDupeAgentTest.class.getClassLoader(), hookBlock(code -> {
            paperGuard(code);
            mojangGuard(code);
            code.return_();
        }));
        List<Opcode> opcodes = opcodes(patched);
        List<Opcode> expected = List.of(
                // Paper's guard, unchanged.
                Opcode.ALOAD_2, Opcode.GETSTATIC, Opcode.INVOKEVIRTUAL, Opcode.IFEQ, Opcode.RETURN,
                // Mojang's, its is(TRIPWIRE_HOOK) now pop; iconst_1.
                Opcode.ALOAD_0, Opcode.ALOAD_1, Opcode.INVOKEVIRTUAL, Opcode.POP, Opcode.ICONST_1,
                Opcode.IFNE, Opcode.ALOAD_2, Opcode.ALOAD_0, Opcode.ALOAD_1, Opcode.INVOKESTATIC,
                Opcode.RETURN, Opcode.RETURN);
        if (!opcodes.equals(expected)) {
            failures.add("one guard: expected " + expected + ", got " + opcodes);
        }

        if (!failures.isEmpty()) {
            failures.forEach(System.err::println);
            System.exit(1);
        }
        System.out.println("HookDupeAgent refuses zero and two guards and rewrites one");
    }

    private static void expectRefusal(List<String> failures, String name, byte[] classfile) {
        try {
            HookDupeAgent.patch(HookDupeAgentTest.class.getClassLoader(), classfile);
            failures.add(name + ": patched, expected a refusal");
        } catch (IllegalStateException expected) {
            System.out.println(name + ": refused: " + expected.getMessage());
        }
    }

    private static byte[] hookBlock(Consumer<CodeBuilder> body) {
        return ClassFile.of().build(HOOK_BLOCK, clazz -> clazz.withMethodBody(
                HookDupeAgent.TARGET_METHOD, CALCULATE_STATE,
                ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC, body));
    }

    /** {@code if (!level.getBlockState(pos).is(TRIPWIRE_HOOK)) { onRemoved(state, level, pos); return; }} */
    private static void mojangGuard(CodeBuilder code) {
        removalAfterTest(code, "TRIPWIRE_HOOK", true);
    }

    private static void removalAfterTest(CodeBuilder code, String block, boolean thenReturn) {
        Label stillThere = code.newLabel();
        code.aload(0).aload(1).invokevirtual(LEVEL, "getBlockState", GET_BLOCK_STATE)
                .getstatic(BLOCKS, block, BLOCK).invokevirtual(BLOCK_STATE, "is", IS)
                .ifne(stillThere)
                .aload(2).aload(0).aload(1).invokestatic(HOOK_BLOCK, "onRemoved", ON_REMOVED);
        if (thenReturn) {
            code.return_();
        }
        code.labelBinding(stillThere);
    }

    /** {@code if (state.is(TRIPWIRE_HOOK)) return;}: an {@code is(TRIPWIRE_HOOK)} branching on {@code ifeq}, as Paper's guard does, and guarding no removal. */
    private static void paperGuard(CodeBuilder code) {
        Label skip = code.newLabel();
        code.aload(2).getstatic(BLOCKS, "TRIPWIRE_HOOK", BLOCK).invokevirtual(BLOCK_STATE, "is", IS)
                .ifeq(skip)
                .return_()
                .labelBinding(skip);
    }

    private static List<Opcode> opcodes(byte[] classfile) {
        MethodModel method = ClassFile.of().parse(classfile).methods().stream()
                .filter(m -> m.methodName().equalsString(HookDupeAgent.TARGET_METHOD))
                .findFirst().orElseThrow();
        return method.code().orElseThrow().elementList().stream()
                .filter(Instruction.class::isInstance)
                .map(e -> ((Instruction) e).opcode())
                .toList();
    }

    private static ClassDesc desc(String internalName) {
        return ClassDesc.ofInternalName(internalName);
    }
}
