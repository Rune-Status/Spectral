package org.spectral.mapping

/**
 * Responsible for parsing a [ClassMapping] from raw text
 * pulled from a file.
 */
object MappingParser {

    /**
     * Parses the text from a mapping file and outputs
     * the constructed [ClassMapping] object.
     *
     * @param rawText String
     * @return ClassMapping
     */
    fun parse(rawText: String): ClassMapping {

        val lines = rawText.split("\n")

        /*
         * Parse the class information
         */
        if(lines.isEmpty()) throw MappingParseException("Mapping file is empty.")

        val classLine = lines[0].split(" ")
        if(classLine.size != 3) throw MappingParseException("Class line is invalid.")

        val className = classLine[1]
        val obfClassName = classLine[2]

        /*
         * The class mapping output object.
         */
        val classMapping = ClassMapping(className, obfClassName)

        /*
         * Parse fields.
         */
        val fieldLines = lines.filter { it.startsWith("\tFIELD") }

        fieldLines.forEach { fieldLine ->
            val fieldLineData = fieldLine.split(" ")
            if(fieldLineData.size != 5) throw MappingParseException("Field line is invalid.")

            val fieldName = fieldLineData[1].substring(0, fieldLineData[1].indexOf(":"))
            val fieldDesc = fieldLineData[1].substring(fieldLineData[1].indexOf(":") + 1, fieldLineData[1].length)
            val obfFieldName = fieldLineData[2].substring(0, fieldLineData[2].indexOf(":"))
            val obfFieldDesc = fieldLineData[2].substring(fieldLineData[2].indexOf(":") + 1, fieldLineData[2].length)

            val fieldOwner = fieldLineData[3]
            val obfFieldOwner = fieldLineData[4]

            val fieldMapping = FieldMapping(fieldName, fieldDesc, fieldOwner, obfFieldName, obfFieldDesc, obfFieldOwner)
            classMapping.fields.add(fieldMapping)
        }

        /*
         * Parse Methods
         */
        val methodLines = lines.filter { it.startsWith("\tMETHOD") }

        methodLines.forEach { methodLine ->
            val methodLineData = methodLine.split(" ")
            if(methodLineData.size != 5) throw MappingParseException("Method line is invalid.")

            val methodName = methodLineData[1].substring(0, methodLineData[1].indexOf(":"))
            val methodDesc = methodLineData[1].substring(methodLineData[1].indexOf(":") + 1, methodLineData[1].length)
            val obfMethodName = methodLineData[2].substring(0, methodLineData[2].indexOf(":"))
            val obfMethodDesc = methodLineData[2].substring(methodLineData[2].indexOf(":") + 1, methodLineData[2].length)

            val methodOwner = methodLineData[3]
            val obfMethodOwner = methodLineData[4]

            val methodMapping = MethodMapping(methodName, methodDesc, methodOwner, obfMethodName, obfMethodDesc, obfMethodOwner)
            classMapping.methods.add(methodMapping)
        }

        return classMapping
    }

    /**
     * An exception thrown when a parsing error occurs.
     *
     * @constructor
     */
    class MappingParseException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
}