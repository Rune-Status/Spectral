package org.spectral.asm

import org.objectweb.asm.Opcodes
import org.objectweb.asm.Opcodes.ASM8
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import org.spectral.asm.util.asm
import java.util.ArrayDeque
import java.util.stream.Collectors

/**
 * Represents a java class.
 *
 * @property group ClassGroup
 * @property node ClassNode
 * @property real Boolean
 * @constructor
 */
class Class private constructor(
    val group: ClassGroup,
    val node: ClassNode,
    val real: Boolean
) : Matchable<Class>() {

    /**
     * Creates a real known [Class] object.
     *
     * @param group ClassGroup
     * @param node ClassNode
     * @constructor
     */
    constructor(group: ClassGroup, node: ClassNode) : this(group, node, true)

    /**
     * Creates a non-real unknown [Class] object.
     *
     * @param group ClassGroup
     * @param name String
     * @constructor
     */
    constructor(group: ClassGroup, name: String) : this(group, ClassNode(ASM8), false) {
        this.name = name
        this.parentName = "java/lang/Object"
        this.match = this
    }

    var name: String by asm(node::name)

    var parentName: String by asm(node::superName)

    var access: Int by asm(node::access)

    val interfaceNames: List<String> by asm(node::interfaces)

    val type get() = Type.getObjectType(name)

    var methods: MutableSet<Method> = mutableListOf<Method>().stream().collect(Collectors.toSet())

    var fields: MutableSet<Field> = mutableListOf<Field>().stream().collect(Collectors.toSet())

    var parent: Class? = null

    val children = hashSetOf<Class>()

    val interfaces = hashSetOf<Class>()

    val implementers = hashSetOf<Class>()

    val hierarchy = hashSetOf<Class>()

    val strings = hashSetOf<String>()

    val methodTypeRefs = hashSetOf<Method>()

    val fieldTypeRefs = hashSetOf<Field>()

    /**
     * Gets a method in the current class given the name
     * and descriptor of the method.
     *
     * If no method exists, a non-real method is created.
     *
     * @param name String
     * @param desc String
     * @return Method
     */
    fun getOrCreateMethod(name: String, desc: String): Method {
        var method = methods.firstOrNull { it.name == name && it.desc == desc }
        if(method == null) {
            method = Method(group, this, name, desc)
            methods.add(method)
        }

        return method
    }

    /**
     * Gets a field with a given name and descriptor. If one does not exist,
     * a non-real (unknown) field is created.
     *
     * @param name String
     * @param desc String
     * @return Field
     */
    fun getOrCreateField(name: String, desc: String): Field {
        var field = fields.firstOrNull { it.name == name && it.desc == desc }
        if(field == null) {
            field = Field(group, this, name, desc)
            fields.add(field)
        }

        return field
    }

    fun getMethod(name: String, desc: String): Method? = methods.firstOrNull { it.name == name && it.desc == desc }

    fun getField(name: String, desc: String): Field? = fields.firstOrNull { it.name == name && it.desc == desc }

    /**
     * Resolves a method from the current class or the closest jvm
     * accepted override. If [toInterface] is true, the method resolves to the closest
     * override from an interface implementation.
     *
     * @param name String
     * @param desc String
     * @param toInterface Boolean
     * @return Method?
     */
    fun resolveMethod(name: String, desc: String, toInterface: Boolean): Method? {
        if(!toInterface) {
            var ret = getMethod(name, desc)
            if(ret != null) return ret

            var cls: Class? = parent

            while(cls != null) {
                ret = cls.getMethod(name, desc)
                if(ret != null) return ret

                cls = cls.parent
            }

            return this.resolveInterfaceMethod(name, desc)
        } else {
            var ret = this.getMethod(name, desc)
            if(ret != null) return ret

            ret = this.parent?.getMethod(name, desc)
            if(ret != null && (ret.access and (Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC) == Opcodes.ACC_PUBLIC)) return ret

            return this.resolveInterfaceMethod(name, desc)
        }
    }

    /**
     * Resolves a field from the current class or the closest jvm
     * accepted override.
     *
     * @param name String
     * @param desc String
     * @return Field?
     */
    fun resolveField(name: String, desc: String): Field? {
        var ret = this.getField(name, desc)
        if(ret != null) return ret

        if(this.interfaces.isNotEmpty()) {
            val queue = ArrayDeque<Class>()
            queue.addAll(this.interfaces)

            var cls = queue.pollFirst()
            while(cls != null) {
                ret = cls.getField(name, desc)
                if(ret != null) return ret

                cls.interfaces.forEach {
                    queue.addFirst(it)
                }

                cls = queue.pollFirst()
            }
        }

        var cls: Class? = this.parent
        while(cls != null) {
            ret = cls.getField(name, desc)
            if(ret != null) return ret

            cls = cls.parent
        }

        return null
    }

    private fun resolveInterfaceMethod(name: String, desc: String): Method? {
        val queue = ArrayDeque<Class>()
        val queued = hashSetOf<Class>()

        var cls = this.parent

        while(cls != null) {
            cls.interfaces.forEach {
                if(queued.add(it)) queue.add(it)
            }
            cls = cls.parent
        }

        if(queue.isEmpty()) return null

        val matches = hashSetOf<Method>()
        var foundNonAbstract = false

        cls = queue.poll()
        while(cls != null) {
            val ret = cls.getMethod(name, desc)
            if(ret != null && (ret.access and (Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC) == 0)) {
                matches.add(ret)

                if((ret.access and Opcodes.ACC_ABSTRACT) == 0) {
                    foundNonAbstract = true
                }
            }

            cls.interfaces.forEach {
                if(queued.add(it)) queue.add(it)
            }

            cls = queue.poll()
        }

        if(matches.isEmpty()) return null
        if(matches.size == 1) return matches.iterator().next()

        if(foundNonAbstract) {
            val it = matches.iterator()
            while(it.hasNext()) {
                val m = it.next()

                if((m.access and Opcodes.ACC_ABSTRACT) != 0) {
                    it.remove()
                }
            }

            if(matches.size == 1) return matches.iterator().next()
        }

        val it = matches.iterator()
        while(it.hasNext()) {
            val m = it.next()
            cmpLoop@ for(m2 in matches) {
                if(m2 == m) continue

                if(m2.owner.interfaces.contains(m.owner)) {
                    it.remove()
                    break
                }

                queue.addAll(m2.owner.interfaces)

                cls = queue.poll()
                while(cls != null) {
                    if(cls.interfaces.contains(m.owner)) {
                        it.remove()
                        queue.clear()
                        break@cmpLoop
                    }

                    cls = queue.poll()
                }
            }
        }

        return matches.iterator().next()
    }

    override fun toString(): String {
        return name
    }
}