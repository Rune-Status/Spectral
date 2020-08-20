package org.spectral.asm

import org.jgrapht.traverse.DepthFirstIterator
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import org.objectweb.asm.tree.AbstractInsnNode.*
import org.spectral.asm.util.Interpreter
import java.lang.reflect.Modifier
import java.util.stream.Collectors

class FeatureExtractor(val group: ClassGroup) {

    fun process() {
        /*
         * Processing Pass A
         */
        group.forEach { c ->
            this.processA(c)
        }

        /*
         * Processing Pass B
         */
        group.forEach { c ->
            this.processB(c)
        }

        /*
         * Processing Pass C
         */
        group.forEach { c ->
            this.processC(c)
        }

        /*
         * Processing Pass D
         */
        group.forEach { c ->
            this.processD(c)
        }
    }

    private fun processA(cls: Class) {
        if(!cls.real) return

        /*
         * Build the inheritors
         */
        cls.parent = group[cls.parentName]
        cls.parent?.children?.add(cls)

        cls.interfaceNames.forEach {
            cls.interfaces.add(group[it])
        }

        cls.interfaces.forEach { it.implementers.add(cls) }

        /*
         * Extract the class methods.
         */
        cls.methods = cls.node.methods.map { Method(cls.group, cls, it) }.stream().collect(Collectors.toSet())

        /*
         * Extract the class fields.
         */
        cls.fields = cls.node.fields.map { Field(cls.group, cls, it) }.stream().collect(Collectors.toSet())

        /*
         * Extract Strings from methods and fields within the class.
         */
        cls.methods.forEach { m ->
            extractStrings(m.instructions.iterator(), cls.strings)
        }

        cls.fields.forEach { f ->
            if(f.value is String) {
                cls.strings.add(f.value as String)
            }
        }

        /*
         * Build the hierarchy graph edges.
         */

        // class parent edges.
        if(cls.real && cls.parent!!.real) {
            cls.group.hierarchyGraph.addEdge(cls, cls.parent)
        }

        if(cls.real) {
            cls.interfaces.filter { it.real }.forEach { i ->
                cls.group.hierarchyGraph.addEdge(cls, i)
            }
        }
    }

    private fun processB(cls: Class) {
        /*
         * Build the class object hierarchy.
         */
        if(!cls.real) return

        val hierarchyIterator = DepthFirstIterator(cls.group.hierarchyGraph, cls)
        while(hierarchyIterator.hasNext()) {
            cls.hierarchy.add(hierarchyIterator.next())
        }
    }

    private fun processC(cls: Class) {
        if(!cls.real) return

        /*
         * Build method overrides
         */
        cls.methods.forEach { m ->
            val methodOverrides = mutableListOf<Method>()

            m.owner.hierarchy.forEach {
                it.methods
                    .filter { !it.isStatic && !it.isPrivate }
                    .filter { it.name == m.name && it.desc == m.desc }
                    .filter { it.owner != m.owner }.forEach { methodOverrides.add(it) }
            }

            m.overrides.addAll(methodOverrides)
        }

        /*
         * Build Field Overrides
         */
        cls.fields.forEach { f ->
            val fieldOverrides = mutableListOf<Field>()

            f.owner.hierarchy.forEach {
                it.fields
                    .filter { !it.isStatic && !it.isPrivate }
                    .filter { it.name == f.name && it.desc == f.desc }
                    .filter { it.owner != f.owner }.forEach { fieldOverrides.add(it) }
            }

            f.overrides.addAll(fieldOverrides)
        }

        cls.methods.stream().collect(Collectors.toSet()).forEach { m ->
            /*
             * Process method instructions
             */
            this.processMethodInsns(m)
        }
    }

    private fun processD(cls: Class) {
        /*
         * Process field initializers
         */
        cls.fields.forEach { f ->
            if(f.writeRefs.size == 1) {
                /*
                 * If the field has only one value initialized for
                 * the entire runtime duration, we can grab the initializer
                 * instructions to calculate its value.
                 */
                f.initializer = Interpreter.extractInitializer(f)
            }
        }
    }

    /**
     * Processes the method instructions for a given method.
     *
     * @param method Method
     */
    private fun processMethodInsns(method: Method) {
        if(!method.real) {
            return
        }

        val it = method.instructions.iterator()
        while(it.hasNext()) {
            val insn = it.next()

            when(insn.type) {
                /*
                 * Method invocation instruction
                 */
                METHOD_INSN -> {
                    val ins = insn as MethodInsnNode
                    handleMethodInvocation(method, ins.owner, ins.name, ins.desc,
                        (ins.itf || ins.opcode != INVOKEINTERFACE))
                }

                /*
                 * Field read / write instruction
                 */
                FIELD_INSN -> {
                    val ins = insn as FieldInsnNode
                    val owner = group[ins.owner]
                    var dst = owner.resolveField(ins.name, ins.desc)

                    if(dst == null) {
                        dst = Field(group, owner, ins.name, ins.desc)
                        owner.fields.add(dst)
                    }

                    /*
                     * Determine if the field instruction was a read or write.
                     */
                    if(ins.opcode == GETSTATIC || ins.opcode == GETFIELD) {
                        dst.readRefs.add(method)
                        method.fieldReadRefs.add(dst)
                    } else {
                        dst.writeRefs.add(method)
                        method.fieldWriteRefs.add(dst)
                    }

                    dst.owner.methodTypeRefs.add(method)
                    method.classRefs.add(dst.owner)
                }

                /*
                 * Type instruction
                 */
                TYPE_INSN -> {
                    val ins = insn as TypeInsnNode
                    val dst = group[ins.desc]

                    dst.methodTypeRefs.add(method)
                    method.classRefs.add(dst)
                }
            }
        }
    }

    private fun handleMethodInvocation(method: Method, rawOwner: String, name: String, desc: String, toInterface: Boolean) {
        val owner = group[rawOwner]
        var dst = owner.resolveMethod(name, desc, toInterface)

        if(dst == null) {
            dst = Method(method.group, owner, name, desc)
            owner.methods.add(dst)
        }

        dst.refsIn.add(method)
        method.refsOut.add(dst)
        dst.owner.methodTypeRefs.add(method)
        method.classRefs.add(dst.owner)
    }



    companion object {

        /**
         * Extracts the strings from an interable instruction set and adds them
         * to the [out] string set.
         *
         * @param it Iterator<AbstractInsnNode>
         * @param out MutableSet<String>
         */
        fun extractStrings(it: Iterator<AbstractInsnNode>, out: MutableSet<String>) {
            while(it.hasNext()) {
                val insn = it.next()

                if(insn is LdcInsnNode) {
                    if(insn.cst is String) {
                        out.add(insn.cst as String)
                    }
                }
            }
        }

        /**
         * Extracts number constants form an inferable instruction set and adds them
         * to the correct set.
         *
         * @param it Iterator<AbstractInsnNode>
         * @param ints MutableSet<Int>
         * @param longs MutableSet<Long>
         * @param floats MutableSet<Float>
         * @param doubles MutableSet<Double>
         */
        fun extractNumbers(
            it: Iterator<AbstractInsnNode>,
            ints: MutableSet<Int>,
            longs: MutableSet<Long>,
            floats: MutableSet<Float>,
            doubles: MutableSet<Double>
        ) {
            while(it.hasNext()) {
                val insn = it.next()

                if(insn is LdcInsnNode) {
                    handleNumberValue(insn.cst, ints,longs,floats, doubles)
                } else if(insn is IntInsnNode) {
                    ints.add(insn.operand)
                }
            }
        }

        fun handleNumberValue(
            number: Any,
            ints: MutableSet<Int>,
            longs: MutableSet<Long>,
            floats: MutableSet<Float>,
            doubles: MutableSet<Double>
        ) {
            when(number) {
                is Int -> ints.add(number)
                is Long -> longs.add(number)
                is Float -> floats.add(number)
                is Double -> doubles.add(number)
            }
        }
    }
}