package com.oasissmp.stringdupe;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.Instruction;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LoadInstruction;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;

/**
 * Puts back the tripwire string dupe Mojang removed in 1.21.2 (MC-59471).
 *
 * <p>When a tripwire line's attached state flips,
 * {@code TripWireHookBlock.calculateState} rewrites every string between the
 * hooks from the states it read at the start. Since 1.21.2 it skips a
 * position that is no longer tripwire or a hook, so string that water washed
 * away mid-update, and that has already dropped as an item, stays gone.
 * Through 1.21.1 it wrote the string back, which is what string dupers run
 * on. This makes {@code state.is(TRIPWIRE) || state.is(TRIPWIRE_HOOK)} in
 * that loop always true, leaving the rest of the condition (Paper's
 * {@code disable-tripwire-updates}) as it is.
 */
public final class StringDupeAgent {

    private static final String TARGET_CLASS = "net/minecraft/world/level/block/TripWireHookBlock";
    private static final String TARGET_METHOD = "calculateState";
    private static final String BLOCKS = "net/minecraft/world/level/block/Blocks";
    private static final String BLOCK_STATE = "net/minecraft/world/level/block/state/BlockState";
    private static final String IS_DESCRIPTOR = "(Lnet/minecraft/world/level/block/Block;)Z";

    private StringDupeAgent() {}

    public static void premain(String args, Instrumentation instrumentation) {
        instrumentation.addTransformer(new Transformer(instrumentation));
    }

    private static final class Transformer implements ClassFileTransformer {

        private final Instrumentation instrumentation;

        Transformer(Instrumentation instrumentation) {
            this.instrumentation = instrumentation;
        }

        @Override
        public byte[] transform(
                ClassLoader loader,
                String className,
                Class<?> classBeingRedefined,
                ProtectionDomain protectionDomain,
                byte[] classfileBuffer) {
            if (!TARGET_CLASS.equals(className)) {
                return null;
            }
            instrumentation.removeTransformer(this);
            // The JVM swallows an exception thrown from here and loads the
            // class unchanged, so the failure is reported before it is lost.
            try {
                byte[] patched = patch(loader, classfileBuffer);
                System.out.println("[StringDupeAgent] Tripwire string duping restored");
                return patched;
            } catch (Throwable e) {
                System.err.println("[StringDupeAgent] Tripwire string duping NOT restored, "
                        + "the class loads unchanged");
                e.printStackTrace();
                return null;
            }
        }
    }

    static byte[] patch(ClassLoader loader, byte[] classfile) {
        // Stack maps are regenerated, which can need the hierarchy of
        // Minecraft's classes: only the loader defining this class sees them.
        ClassFile classFile = ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(
                ClassHierarchyResolver.ofResourceParsing(loader)));
        ClassModel model = classFile.parse(classfile);
        MethodModel target = onlyMethod(model);
        List<Integer> guards = findGuards(code(target).elementList());
        if (guards.size() != 1) {
            throw new IllegalStateException("expected one is(TRIPWIRE) || is(TRIPWIRE_HOOK) in "
                    + TARGET_METHOD + ", found " + guards.size());
        }
        int guard = guards.getFirst();
        byte[] patched = classFile.transformClass(model, ClassTransform.transformingMethodBodies(
                method -> method.methodName().equals(target.methodName())
                        && method.methodType().equals(target.methodType()),
                CodeTransform.ofStateful(() -> new TripwireCheckToTrue(guard))));
        int left = findGuards(code(onlyMethod(classFile.parse(patched))).elementList()).size();
        if (left != 0) {
            throw new IllegalStateException("the rewrite left " + left + " guard in place");
        }
        return patched;
    }

    /**
     * Replaces {@code getstatic TRIPWIRE; invokevirtual is}, the guard's
     * second and third instructions, with {@code pop; iconst_1}: the state is
     * still consumed and the test is true. The transform is handed the
     * method's elements in {@code elementList}'s order but as new objects, so
     * they are found by position, and checked to be those two.
     */
    private static final class TripwireCheckToTrue implements CodeTransform {

        private final int tripwireField;
        private final int tripwireCheck;
        private int index;

        TripwireCheckToTrue(int guard) {
            tripwireField = guard + 1;
            tripwireCheck = guard + 2;
        }

        @Override
        public void accept(CodeBuilder builder, CodeElement element) {
            int at = index++;
            if (at == tripwireField) {
                if (!(element instanceof Instruction i && isBlock(i, "TRIPWIRE"))) {
                    throw new IllegalStateException(
                            "expected getstatic TRIPWIRE at " + at + ", found " + element);
                }
            } else if (at == tripwireCheck) {
                if (!(element instanceof Instruction i && isStateIs(i))) {
                    throw new IllegalStateException(
                            "expected BlockState.is at " + at + ", found " + element);
                }
                builder.pop().iconst_1();
            } else {
                builder.with(element);
            }
        }
    }

    private static MethodModel onlyMethod(ClassModel model) {
        List<MethodModel> methods = model.methods().stream()
                .filter(method -> method.methodName().equalsString(TARGET_METHOD))
                .toList();
        if (methods.size() != 1) {
            throw new IllegalStateException(
                    "expected one " + TARGET_METHOD + " method, found " + methods.size());
        }
        return methods.getFirst();
    }

    private static CodeModel code(MethodModel method) {
        return method.code()
                .orElseThrow(() -> new IllegalStateException(TARGET_METHOD + " has no code"));
    }

    /**
     * The element index of the first instruction of each
     * {@code x.is(Blocks.TRIPWIRE) || x.is(Blocks.TRIPWIRE_HOOK)}, counting
     * only those whose second and third instructions are the next two
     * elements, with no label or line number between: those two are what
     * gets rewritten.
     */
    private static List<Integer> findGuards(List<CodeElement> elements) {
        List<Integer> at = new ArrayList<>();
        for (int i = 0; i < elements.size(); i++) {
            if (elements.get(i) instanceof Instruction) {
                at.add(i);
            }
        }
        List<Integer> guards = new ArrayList<>();
        for (int n = 0; n + 8 <= at.size(); n++) {
            Instruction[] window = new Instruction[8];
            for (int k = 0; k < 8; k++) {
                window[k] = (Instruction) elements.get(at.get(n + k));
            }
            if (isGuard(window) && at.get(n + 2) == at.get(n) + 2) {
                guards.add(at.get(n));
            }
        }
        return guards;
    }

    private static boolean isGuard(Instruction[] w) {
        return w[0] instanceof LoadInstruction first
                && isBlock(w[1], "TRIPWIRE")
                && isStateIs(w[2])
                && w[3].opcode() == Opcode.IFNE
                && w[4] instanceof LoadInstruction second
                && second.slot() == first.slot()
                && isBlock(w[5], "TRIPWIRE_HOOK")
                && isStateIs(w[6])
                && w[7].opcode() == Opcode.IFEQ;
    }

    private static boolean isBlock(Instruction instruction, String name) {
        return instruction instanceof FieldInstruction field
                && field.opcode() == Opcode.GETSTATIC
                && field.owner().asInternalName().equals(BLOCKS)
                && field.name().equalsString(name);
    }

    private static boolean isStateIs(Instruction instruction) {
        return instruction instanceof InvokeInstruction invoke
                && invoke.opcode() == Opcode.INVOKEVIRTUAL
                && invoke.owner().asInternalName().equals(BLOCK_STATE)
                && invoke.name().equalsString("is")
                && invoke.typeSymbol().descriptorString().equals(IS_DESCRIPTOR);
    }
}
