package org.spectral.mapper.execution

import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.spectral.asm.Method
import org.spectral.asm.desc
import org.spectral.asm.name
import org.spectral.mapper.util.ExecutionUtil
import java.util.ArrayDeque

/**
 * Represents a method execution model for simulating
 * how a method interacts with the JVM.
 *
 * @property method MethodNode
 * @constructor
 */
class Execution(val method: Method) {

    private val analyzer = BlockAnalyzer()

    private val frames = analyzer.analyze(method.owner.name, method.node)

    private val blocks = hashMapOf<Int, MutableList<Block>>()

    private var invocationLayer = 0

    /**
     * Gets whether the execution has terminated.
     */
    var terminated = false

    /**
     * Gets whether the execution has been paused.
     */
    var paused = false

    /**
     * The current block the execution is on.
     */
    var currentBlock: Block? = null

    /**
     * The current instruction index.
     */
    var currentInsnIndex = 0

    /**
     * The current method instruction we are on.
     */
    var currentInsn: AbstractInsnNode? = null

    /**
     * Collections of visited control flow [Blocks].
     * These are used to ensure we only process control flow
     * blocks once.
     */
    private val visitedBranchBlocks = mutableListOf<Block>()
    private val visitedTrunkBlocks = mutableListOf<Block>()

    /**
     * The block instruction index to jump back
     * to after executing an extracted method.
     */
    private var jumpBackInsnIndexes = ArrayDeque<Int>()

    /**
     * Initialize the execution
     */
    fun initialize() {
        blocks[invocationLayer] = analyzer.blocks

        if(blocks[invocationLayer]!!.isEmpty()) return
        currentBlock = blocks[invocationLayer]!!.first()
        currentInsnIndex = currentBlock!!.startIndex
        currentInsn = currentBlock!!.instructions[0]
    }

    /**
     * Steps forward in the execution.
     */
    fun step() {
        /*
         * If the execution is terminated, we
         * do not want anything to happen.
         */
        if(terminated || currentBlock == null) {
            paused = true
            return
        }

        /*
         * Increment the current instruction index.
         * We need to make sure that we do not need to jump
         * into a new block.
         */
        currentInsnIndex++

        /*
         * Check if we need to change the current block.
         */
        if(currentInsnIndex >= currentBlock!!.endIndex - 1) {
            /*
             * If the next block is null,
             * we have reached the end of the execution
             * or it has been terminated.
             *
             * We want to update the termination field
             * and return nothing.
             */
            val nextBlock = nextBlock()
                ?:

                /*
                 * The only exempt case is where we need to jump back
                 * out of a static method invocation.
                 */
                if(jumpBackInsnIndexes.isNotEmpty()) {
                    stepOutInvoke()
                    return
                } else {
                    terminated = true
                    paused = true
                    return
                }

            this.stepInto(nextBlock)
        }

        /*
         * Update the current instruction from the incremented instruction
         * index.
         */
        currentInsn = currentBlock!!.instructions[currentInsnIndex - currentBlock!!.startIndex]

        /*
         * Check if the updated [currentInsn] is a INVOKESTATIC
         * meaning it was likely an inlined method at one point or in the
         * comparing method.
         *
         * If so, step into the method invocation instruction if its
         * a valid place to step into.
         */
        if(ExecutionUtil.isInlineable(method.group, currentInsn!!)) {
            val methodInvokeInsn = currentInsn as MethodInsnNode
            //stepIntoInvoke(methodInvokeInsn)
        }
    }

    /**
     * Gets the next [Block] we should step into.
     *
     * @return Block?
     */
    fun nextBlock(): Block? {
        /*
         * If the current block has any branches we have not already been
         * down and visited.
         */
        if(currentBlock!!.branches.isNotEmpty() && currentBlock!!.branches.any { !visitedBranchBlocks.contains(it) }) {
            val branchBlock = currentBlock!!.branches.first { !visitedBranchBlocks.contains(it) }
            visitedBranchBlocks.add(branchBlock)
            branchBlock.trunk = currentBlock
            return branchBlock
        }
        /*
         * If the current block has a continuing block after it
         */
        else if(currentBlock!!.next != null) {
            return currentBlock!!.next!!
        }

        else if(jumpBackInsnIndexes.isNotEmpty()) {
            return null
        }

        /*
         * If we are at the end of a branch and its a dead end.
         * ( Examples would be try/catch exceptions.)
         *
         * Jump back to the trunk block which will be where we branched off from.
         */
        else if(currentBlock!!.origin.trunk != null && currentBlock!!.origin.trunk!! !in visitedTrunkBlocks) {
            val trunkBlock = currentBlock!!.origin.trunk!!
            visitedTrunkBlocks.add(trunkBlock)
            return trunkBlock
        }
        else {
            return null
        }
    }

    /**
     * Steps the execution into a given [block]
     *
     * @param block Block
     */
    fun stepInto(block: Block) {
        currentBlock = block
        currentInsnIndex = block.startIndex
        currentInsn = block.instructions[0]
    }

    /**
     * Steps into an invocation method.
     * This is how we deal with inlined/extracted methods
     * that the OSRS obfuscator will do.
     *
     * NOTE, If the invocation is not a inlineable method,
     * this method will return FALSE indicating that we should
     * skip the stepInto()
     *
     * @param insn MethodInsnNode
     * @return [Boolean]
     */
    private fun stepIntoInvoke(insn: MethodInsnNode): Boolean {
        if(insn.opcode != Opcodes.INVOKESTATIC) return false

        val m = method.group[insn.owner]?.methods?.filter {
            it.name == insn.name && it.desc == it.desc
        } ?: return false

        if(m.size > 1) return false
        if(!m.first().isStatic) return false

        /*
         * Lets grabs the [Block]s of the method.
         */
        val b = BlockAnalyzer().apply { this.analyze(m.first().owner.name, m.first().node) }.blocks

        /*
         * Increment the invocation layer and add the [b] blocks
         * to the invokeBlocks hashmap with the invocation layer
         * as the key.
         */
        invocationLayer++
        blocks[invocationLayer] = b

        /*
         * Set the last block's next value to be
         * our current block.
         *
         * This is so when the extracted static method's execution finishes,
         * we jump back to where we are.
         */
        jumpBackInsnIndexes.push(currentInsnIndex)

        return if(blocks[invocationLayer]!!.isNotEmpty()) {
            stepInto(blocks[invocationLayer]!!.first())
            true
        } else {
            false
        }
    }

    /**
     * Steps out of a static method invocation.
     */
    private fun stepOutInvoke() {
        /*
         * Lets check this method was not incorrectly called.
         */
        if(jumpBackInsnIndexes.isEmpty()) throw IllegalStateException("No where to jump out of a method invoke.")

        val stepOutBlock = findBlock(jumpBackInsnIndexes.peek() + 1)
            ?: throw NullPointerException("Unable to locate a block in invocation layer ${invocationLayer - 1} to jump out of method invoke at instruction index ${jumpBackInsnIndexes.peek()}.")

        /*
         * Update the current data
         */
        currentBlock = stepOutBlock
        currentInsnIndex = jumpBackInsnIndexes.peek() + 1
        currentInsn = stepOutBlock.instructions[currentInsnIndex - currentBlock!!.startIndex]

        /*
         * Reset the jump back index field to prevent
         * a loop of jump back's
         */
        jumpBackInsnIndexes.pop()
        invocationLayer--

        currentBlock = nextBlock()

        step()
    }

    /**
     * Finds a [Block] at a given instruction index.
     *
     * @param index Int
     * @return Block?
     */
    private fun findBlock(index: Int): Block? {
        for(i in invocationLayer downTo 0) {
            blocks[invocationLayer - i]!!.forEach { block ->
                if(index >= block.startIndex && index <= (block.endIndex - 1)) {
                    return block
                }
            }
        }

        return null
    }
}