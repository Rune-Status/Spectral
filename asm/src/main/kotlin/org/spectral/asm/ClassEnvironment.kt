package org.spectral.asm

import org.spectral.asm.util.JarUtil
import java.io.File

/**
 * Represents a environment of two [ClassGroup] objects.
 *
 * @property groupA The [ClassGroup] object A
 * @property groupB The [ClassGroup] object B
 * @constructor
 */
class ClassEnvironment private constructor() {

    lateinit var groupA: ClassGroup
    lateinit var groupB: ClassGroup

    /**
     * A collection of non-real classes which both [ClassGroup]s
     * share. Typically these are jvm std library classes.
     */
    val sharedClasses = hashSetOf<Class>()

    /**
     * Initializes both groups.
     */
    fun init() {
        groupA.init()
        groupB.init()
    }

    /**
     * Adds a [Class] to both [groupA] and [groupB]
     *
     * @param cls Class
     */
    fun addSharedClass(cls: Class) {
        groupA.add(cls)
        groupB.add(cls)
        sharedClasses.add(cls)
    }

    companion object {
        /**
         * Creates an initializes a [ClassEnvironment] from two
         * given JAR files.
         *
         * @param jarFileA File
         * @param jarFileB File
         * @return ClassEnvironment
         */
        fun init(jarFileA: File, jarFileB: File): ClassEnvironment {
            val env = ClassEnvironment()

            env.groupA = ClassGroup(env, JarUtil.loadJar(jarFileA))
            env.groupB = ClassGroup(env, JarUtil.loadJar(jarFileB))
            env.init()

            return env
        }

        /**
         * Initializes a new class environment with two given
         * class groups.
         *
         * @param groupA ClassGroup
         * @param groupB ClassGroup
         * @return ClassEnvironment
         */
        fun init(groupA: ClassGroup, groupB: ClassGroup): ClassEnvironment {
            val env = ClassEnvironment()

            env.groupA = groupA
            env.groupB = groupB
            env.init()

            return env
        }
    }
}