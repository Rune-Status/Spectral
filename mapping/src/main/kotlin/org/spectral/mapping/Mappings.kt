package org.spectral.mapping

import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode
import org.spectral.asm.Field
import org.spectral.asm.Method
import org.spectral.asm.desc
import org.spectral.asm.name
import org.spectral.mapper.MatchGroup
import org.tinylog.kotlin.Logger
import java.io.File
import java.nio.file.Files

/**
 * Represents a memory model of the name mappings of the
 * obfuscated Jagex gamepack.
 */
class Mappings {

    /**
     * The class mappings inside this object
     */
    val classes = mutableListOf<ClassMapping>()

    /**
     * Initializes the mappings from a [MatchGroup]
     *
     * @param matches MatchGroup
     */
    fun load(matches: MatchGroup) {
        /*
         * Get the classes first.
         */
        matches.groupA.forEach {
            val clsA = it
            val clsB = matches[clsA] as ClassNode?

            val classMapping = ClassMapping(clsA.name, if(clsB == null) "?" else clsB.name)

            /*
             * Get the fields that are parented to [clsA]
             */
            matches.matches.keySet().filterIsInstance<Field>().filter { it.owner == clsA }.forEach {
                val fieldA = it
                val fieldB = matches[fieldA] as Field? ?: throw NullPointerException("No match found for field key: '${fieldA}'.")

                val fieldMapping = FieldMapping(fieldA.name, fieldA.desc, fieldA.owner.name, fieldB.name, fieldB.desc, fieldB.owner.name)
                classMapping.fields.add(fieldMapping)
            }

            /*
             * Get the methods that are parented to [clsA]
             */
            matches.matches.keySet().filterIsInstance<Method>().filter { it.owner == clsA }.forEach {
                val methodA = it
                val methodB = matches[methodA] as Method? ?: throw NullPointerException("No match found for method key: '${methodA}'.")

                val methodMapping = MethodMapping(methodA.name, methodA.desc, methodA.owner.name, methodB.name, methodB.desc, methodB.owner.name)
                classMapping.methods.add(methodMapping)
            }

            /*
             * Add the class mapping to the [classes] list.
             */
            classes.add(classMapping)
        }
    }

    /**
     * Exports the mappings to a directory file format.
     *
     * @param folder File
     */
    fun export(folder: File) {
        Logger.info("Exporting mappings to folder: '${folder.absolutePath}'")

        folder.delete()
        folder.mkdirs()

        val path = folder.toPath()
        classes.forEach { c ->
            val classPath = path.resolve(c.name + ".mapping")

            Files.newBufferedWriter(classPath).use { writer ->
                writer.write(c.toString())
            }
        }

        Logger.info("Completed export of mappings.")
    }
}