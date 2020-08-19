package org.spectral.asm.util

import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.ClassReader
import java.io.File
import java.nio.file.Path
import java.util.jar.JarFile

/**
 * Contains utility functions for dealing with
 * the IO side of jar files.
 */
object JarUtil {

    /**
     * Loads and extracts a collection of [ClassNode] objects from
     * each class within a given [jarFile] jar File.
     *
     * @param jarFile File
     * @return Collection<ClassNode>
     */
    fun loadJar(jarFile: File): Collection<ClassNode> {
        val nodes = mutableListOf<ClassNode>()
        JarFile(jarFile).use { jar ->
            jar.entries().asSequence()
                .filter { it.name.endsWith(".class") }
                .forEach {
                    val node = ClassNode()
                    val reader = ClassReader(jar.getInputStream(it))
                    reader.accept(node, ClassReader.EXPAND_FRAMES)
                    nodes.add(node)
                }
        }

        return nodes
    }

}