package org.spectral.asm

import org.jgrapht.traverse.DepthFirstIterator
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.LdcInsnNode
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
    }

    private fun processA(cls: Class) {
        if(!cls.real) return

        /*
         * Build the inheritors
         */
        cls.parent = group[cls.parentName]
        cls.parent.children.add(cls)

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
        if(cls.real && cls.parent.real) {
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

        val classHierarchy = hashSetOf<Class>()
        while(hierarchyIterator.hasNext()) {
            classHierarchy.add(hierarchyIterator.next())
        }

        cls.hierarchy.addAll(classHierarchy)
    }

    private fun processC(cls: Class) {
        if(!cls.real) return

        /*
         * Build method overrides
         */
        cls.methods.forEach { m ->
            val methodOverrides = m.owner.hierarchy.flatMap { it.methods }
                .filter { !it.isStatic && !it.isPrivate }
                .filter { it.name == m.name && it.desc == m.desc }
                .filter { it.owner != m.owner }

            val comparator = compareBy<Method> { !Modifier.isAbstract(it.access) }
                .thenByDescending { m.owner.hierarchy.indexOf(it.owner) }

            m.overrides.addAll(methodOverrides.sortedWith(comparator))
        }

        /*
         * Build Field Overrides
         */
        cls.fields.forEach { f ->
            val fieldOverrides = f.owner.hierarchy.flatMap { it.fields }
                .filter { !it.isStatic && !it.isPrivate }
                .filter { it.name == f.name && it.desc == f.desc }
                .filter { it.owner != f.owner }

            val comparator = compareBy<Field> { !Modifier.isAbstract(it.access) }
                .thenByDescending { f.owner.hierarchy.indexOf(it.owner) }

            f.overrides.addAll(fieldOverrides.sortedWith(comparator))
        }

        cls.methods.forEach { m ->
            /*
             * Process method instructions
             */
            this.processMethodInsns(m)
        }
    }

    /**
     * Processes the method instructions for a given method.
     *
     * @param method Method
     */
    private fun processMethodInsns(method: Method) {

    }

    private fun handleMethodInvocation(method: Method, rawOwner: String, name: String, desc: String, toInterface: Boolean) {
        val owner = group[Type.getType(rawOwner).internalName]
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
                    when(insn.cst) {
                        is Int -> ints.add(insn.cst as Int)
                        is Long -> longs.add(insn.cst as Long)
                        is Float -> floats.add(insn.cst as Float)
                        is Double -> doubles.add(insn.cst as Double)
                    }
                } else if(insn is IntInsnNode) {
                    ints.add(insn.operand)
                }
            }
        }
    }
}