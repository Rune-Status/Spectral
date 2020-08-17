package org.spectral.mapping

import org.tinylog.kotlin.Logger
import java.io.File
import java.io.IOException
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
     * Loads and parses mappings from a directory.
     *
     * @param folder File
     */
    fun load(folder: File) {
       Logger.info("Loading mappings from directory: '${folder.path}'.")

        val mappingFiles = folder.listFiles { _, name -> name.endsWith(".mapping") } ?: throw IOException()

        if(mappingFiles.isEmpty()) {
            Logger.error("No mapping files found in folder: '${folder.path}'.")
            return
        }

        mappingFiles.forEach { f ->
            Logger.info("Loading mapping file: '${f.name}'")

            val rawText = Files.newBufferedReader(f.toPath()).use { reader ->
                return@use reader.readText()
            }

            val classMapping = MappingParser.parse(rawText)

            classes.add(classMapping)
        }

        Logger.info("Successfully loaded ${classes.size} class mappings.")
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