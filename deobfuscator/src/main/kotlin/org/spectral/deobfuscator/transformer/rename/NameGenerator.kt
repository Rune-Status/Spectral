package org.spectral.deobfuscator.transformer.rename

import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.SimpleRemapper
import org.objectweb.asm.tree.ClassNode
import org.spectral.deobfuscator.asm.ClassGroupExt
import org.spectral.deobfuscator.Transformer
import org.spectral.deobfuscator.util.isObfuscatedName
import org.tinylog.kotlin.Logger
import java.util.ArrayDeque

/**
 * Generates temporary names for obfuscated classes, method, field, or localVariable
 * which have an obfuscated name.
 */
class NameGenerator : Transformer {

    private var classCounter = 0
    private var methodCounter = 0
    private var fieldCounter = 0
    private var variableCounter = 0

    private val nameMappings = hashMapOf<String, String>()

    override fun transform(group: ClassGroupExt) {
        /*
         * Generate namings.
         */
        this.generateNameMappings(group)

        /*
         * Apply the name mappings
         */
        this.applyNameMappings(group)

        /*
         * Rebuild the group
         */
        group.rebuild()

        Logger.info("Renamed [classes: $classCounter, method: $methodCounter, fields: $fieldCounter, variables: $variableCounter]")
    }

    /**
     * Generate names for all entries in [group].
     * Puts them into the [nameMappings] map.
     *
     * @param group ClassGroup
     */
    private fun generateNameMappings(group: ClassGroupExt) {
        /*
         * Generate class names for only obfuscated class names.
         */
        group.forEach { c ->
            if(c.name.isObfuscatedName) {
                nameMappings[c.name] = "class${++classCounter}"
            }
        }

        /*
         * Generate method names for only obfuscated method names.
         */
        group.forEach classLoop@ { c ->
            c.methods.forEach methodLoop@ { m ->
                if(!m.name.isObfuscatedName) return@methodLoop
                if(m.name.indexOf("<") != -1) return@methodLoop

                val queue = ArrayDeque<ClassNode>()
                queue.add(c)

                while(queue.isNotEmpty()) {
                    val node = queue.pop()
                    if(node != c && node.methods.firstOrNull { it.name == m.name && it.desc == m.desc } != null) {
                        return@methodLoop
                    }

                    val parent = group[node.superName]
                    if(parent != null) {
                        queue.push(parent)
                    }

                    val interfaces = node.interfaces.mapNotNull { group[it] }
                    queue.addAll(interfaces)
                }

                val newName = "method${++methodCounter}"

                queue.add(c)
                while(queue.isNotEmpty()) {
                    val node = queue.pop()
                    val key = node.name + "." + m.name + m.desc
                    nameMappings[key] = newName
                    group.forEach { k ->
                        if(k.superName == node.name || k.interfaces.contains(node.name)) {
                            queue.push(k)
                        }
                    }
                }
            }
        }

        /*
         * Generate field names for only obfuscated field names
         */
        group.forEach classLoop@ { c ->
            c.methods.forEach fieldLoop@ { f ->
                if(!f.name.isObfuscatedName) return@fieldLoop

                val queue = ArrayDeque<ClassNode>()
                queue.add(c)

                while(queue.isNotEmpty()) {
                    val node = queue.pop()
                    if(node != c && node.fields.firstOrNull { it.name == f.name && it.desc == f.desc } != null) {
                        return@fieldLoop
                    }

                    val parent = group[node.superName]
                    if(parent != null) {
                        queue.push(parent)
                    }

                    val interfaces = node.interfaces.mapNotNull { group[it] }
                    queue.addAll(interfaces)
                }

                val newName = "field${++fieldCounter}"

                queue.add(c)
                while(queue.isNotEmpty()) {
                    val node = queue.pop()
                    val key = node.name + "." + f.name
                    nameMappings[key] = newName
                    group.forEach { k ->
                        if(k.superName == node.name || k.interfaces.contains(node.name)) {
                            queue.push(k)
                        }
                    }
                }
            }
        }
    }

    /**
     * Applies the generated names from [nameMappings] using the built-in
     * ASM [SimpleRemapper] object.
     *
     * @param group ClassGroup
     */
    private fun applyNameMappings(group: ClassGroupExt) {
        val remapper = SimpleRemapper(nameMappings)

        group.forEachIndexed { index, c ->
            val newNode = ClassNode()
            c.accept(ClassRemapper(newNode, remapper))
            group[index] = newNode
        }
    }
}