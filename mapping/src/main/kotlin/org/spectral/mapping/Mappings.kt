package org.spectral.mapping

import org.spectral.asm.ClassGroup

/**
 * Represents a model of obfuscation name mappings
 * between Class group revisions.
 */
class Mappings private constructor() {

    /**
     * A list of class mappings
     */
    val classes = mutableListOf<ClassMapping>()

    companion object {

        /**
         * Initializes a [Mappings] model from a [ClassGroup] and it's
         * entries matched elements.
         *
         * @param group ClassGroup
         * @return Mappings
         */
        fun load(group: ClassGroup): Mappings {
            val mappings = Mappings()

            group.classes.filter { it.real }.forEach { c ->
                val classMapping = ClassMapping(c.name, c.match?.name ?: "?")

                c.fields.filter { it.real }.forEach fieldLoop@ { f ->
                    if(!f.hasMatch()) return@fieldLoop

                    val fieldMapping = FieldMapping(c.name, f.name, f.desc, f.match!!.name, f.match!!.desc)
                    classMapping.fields.add(fieldMapping)
                }

                c.methods.filter { it.real }.forEach methodLoop@ { m ->
                    if(!m.hasMatch()) return@methodLoop

                    val methodMapping = MethodMapping(c.name, m.name, m.desc, m.match!!.name, m.match!!.desc)
                    classMapping.methods.add(methodMapping)
                }

                mappings.classes.add(classMapping)
            }

            return mappings
        }
    }
}