package com.oasissmp.hookdupe;

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
 * Puts back tripwire hook duping, which Mojang's own check has blocked since
 * 26.1.
 *
 * <p>In {@code TripWireHookBlock.calculateState}, the receiver hook's updates
 * can remove the source hook's support, so the source hook drops as an item;
 * the method then writes it back, and that is the dupe. Paper's
 * {@code skip-tripwire-hook-placement-validation} (on in
 * {@code data/config/paper-global.yml}) lets that write happen, but since 26.1
 * vanilla returns before it, through
 * {@code if (!level.getBlockState(pos).is(TRIPWIRE_HOOK)) { onRemoved(...); return; }}
 * right after the receiver hook's updates. This makes that test always true,
 * so the method goes on to Paper's check, which the setting turns off.
 *
 * <p>It relies on the setting being on. With it off, Paper's check skips the
 * write and the source hook stays gone, but the {@code onRemoved} this
 * bypasses, which detaches the rest of the line, never runs.
 */
public final class HookDupeAgent {

    static final String TARGET_CLASS = "net/minecraft/world/level/block/TripWireHookBlock";
    static final String TARGET_METHOD = "calculateState";
    static final String BLOCKS = "net/minecraft/world/level/block/Blocks";
    static final String BLOCK_STATE = "net/minecraft/world/level/block/state/BlockState";
    static final String IS_DESCRIPTOR = "(Lnet/minecraft/world/level/block/Block;)Z";
    static final String ON_REMOVED = "onRemoved";

    private HookDupeAgent() {}

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
                System.out.println("[HookDupeAgent] Tripwire hook duping restored");
                return patched;
            } catch (Throwable e) {
                System.err.println("[HookDupeAgent] Tripwire hook duping NOT restored, "
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
            throw new IllegalStateException("expected one is(TRIPWIRE_HOOK) guarding "
                    + ON_REMOVED + " and a return in " + TARGET_METHOD + ", found " + guards.size());
        }
        int guard = guards.getFirst();
        byte[] patched = classFile.transformClass(model, ClassTransform.transformingMethodBodies(
                method -> method.methodName().equals(target.methodName())
                        && method.methodType().equals(target.methodType()),
                CodeTransform.ofStateful(() -> new HookCheckToTrue(guard))));
        int left = findGuards(code(onlyMethod(classFile.parse(patched))).elementList()).size();
        if (left != 0) {
            throw new IllegalStateException("the rewrite left " + left + " guard in place");
        }
        return patched;
    }

    /**
     * Replaces {@code getstatic TRIPWIRE_HOOK; invokevirtual is}, the guard's
     * first two instructions, with {@code pop; iconst_1}: the state already on
     * the stack is consumed and the test is true. The transform is handed the
     * method's elements in {@code elementList}'s order but as new objects, so
     * they are found by position, and checked to be those two.
     */
    private static final class HookCheckToTrue implements CodeTransform {

        private final int hookField;
        private final int hookCheck;
        private int index;

        HookCheckToTrue(int guard) {
            hookField = guard;
            hookCheck = guard + 1;
        }

        @Override
        public void accept(CodeBuilder builder, CodeElement element) {
            int at = index++;
            if (at == hookField) {
                if (!(element instanceof Instruction i && isTripwireHook(i))) {
                    throw new IllegalStateException(
                            "expected getstatic TRIPWIRE_HOOK at " + at + ", found " + element);
                }
            } else if (at == hookCheck) {
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
     * The element index of the {@code getstatic TRIPWIRE_HOOK} of each
     * {@code is(Blocks.TRIPWIRE_HOOK)} test whose false branch falls through
     * loads straight to {@code invokestatic onRemoved} and a {@code return}.
     * The method's other {@code is(TRIPWIRE_HOOK)} tests, Paper's guard among
     * them, branch elsewhere. Counted only when the {@code getstatic} and the
     * {@code invokevirtual} are adjacent elements, with no label or line
     * number between: those two are what gets rewritten.
     */
    private static List<Integer> findGuards(List<CodeElement> elements) {
        List<Integer> at = new ArrayList<>();
        for (int i = 0; i < elements.size(); i++) {
            if (elements.get(i) instanceof Instruction) {
                at.add(i);
            }
        }
        List<Integer> guards = new ArrayList<>();
        for (int n = 0; n + 4 < at.size(); n++) {
            if (at.get(n + 1) == at.get(n) + 1
                    && isTripwireHook(instruction(elements, at, n))
                    && isStateIs(instruction(elements, at, n + 1))
                    && instruction(elements, at, n + 2).opcode() == Opcode.IFNE
                    && fallsThroughToRemoval(elements, at, n + 3)) {
                guards.add(at.get(n));
            }
        }
        return guards;
    }

    private static boolean fallsThroughToRemoval(List<CodeElement> elements, List<Integer> at, int from) {
        int n = from;
        while (n < at.size() && instruction(elements, at, n) instanceof LoadInstruction) {
            n++;
        }
        return n + 1 < at.size()
                && instruction(elements, at, n) instanceof InvokeInstruction invoke
                && invoke.opcode() == Opcode.INVOKESTATIC
                && invoke.owner().asInternalName().equals(TARGET_CLASS)
                && invoke.name().equalsString(ON_REMOVED)
                && instruction(elements, at, n + 1).opcode() == Opcode.RETURN;
    }

    private static Instruction instruction(List<CodeElement> elements, List<Integer> at, int n) {
        return (Instruction) elements.get(at.get(n));
    }

    private static boolean isTripwireHook(Instruction instruction) {
        return instruction instanceof FieldInstruction field
                && field.opcode() == Opcode.GETSTATIC
                && field.owner().asInternalName().equals(BLOCKS)
                && field.name().equalsString("TRIPWIRE_HOOK");
    }

    private static boolean isStateIs(Instruction instruction) {
        return instruction instanceof InvokeInstruction invoke
                && invoke.opcode() == Opcode.INVOKEVIRTUAL
                && invoke.owner().asInternalName().equals(BLOCK_STATE)
                && invoke.name().equalsString("is")
                && invoke.typeSymbol().descriptorString().equals(IS_DESCRIPTOR);
    }
}
