package org.spectral.mapper.matcher

import org.objectweb.asm.tree.*
import org.spectral.asm.Method
import org.spectral.asm.name
import org.spectral.mapper.MatchGroup
import org.spectral.mapper.execution.Execution
import org.spectral.mapper.execution.ParallelExecutor
import org.spectral.mapper.util.ExecutionUtil
import org.spectral.mapper.util.ScoreUtil

/**
 * Responsible for matching [source] -> [targets] methods
 * based on execution simulation and their similarity on how
 * they interact with the JVM stack.
 *
 * @property source MethodNode
 * @property targets Collection<MethodNode>
 * @constructor
 */
class ExecutionMatcher(private val source: Method, private val targets: Collection<Method>) {

    /**
     * Runs the execution matcher.
     *
     * @return Returns the best [Match] or null.
     */
    fun run(): MatchGroup? {
        var highest: MatchGroup? = null
        var conflict = false

        targets.forEach { target ->
            val execution = execute(source, target)

            if(highest == null || execution.score > highest!!.score) {
                highest = execution
                conflict = false
            }
            else if(execution.score == highest!!.score) {
                conflict = true
            }
        }

        if(conflict) {
            //return null
        }

        return highest
    }

    companion object {
        /**
         * The instruction kotlin classes which we want
         * to pause the execution on and compare.
         */
        val comparableInstructions = listOf(
            IntInsnNode::class, VarInsnNode::class, IincInsnNode::class,
            MethodInsnNode::class, FieldInsnNode::class, LdcInsnNode::class,
            TypeInsnNode::class, InvokeDynamicInsnNode::class, JumpInsnNode::class,
            TableSwitchInsnNode::class, LookupSwitchInsnNode::class, MultiANewArrayInsnNode::class
        )

        /**
         * Executes both method executions in parallel in a memory simulator.
         * Analyzes the control flow branches and their instruction
         * similarities.
         *
         * Additionally checks whether the instructions are pushing and popping
         * similar values from the in-memory JVM stack.
         *
         * @param methodA MethodNode
         * @param methodB MethodNode
         * @return Match
         */
        fun execute(methodA: Method, methodB: Method): MatchGroup {
            val matches = MatchGroup(methodA.group, methodB.group)
            matches.cardinalMethodA = methodA
            matches.cardinalMethodB = methodB

            /*
             * The match score for the given executed methods
             */
            var score = 0

            /*
             * Some simple scorings
             */
            score += ScoreUtil.calculateScore(methodA, methodB)

            /*
             * The execution objects for both methods.
             */
            val executionA = Execution(methodA)
            val executionB = Execution(methodB)

            /*
             * The parallel method executor instance.
             */
            val parallelExecutor = ParallelExecutor(executionA, executionB)
            parallelExecutor.initialize()

            /*
             * Set the execution pause predicate.
             */

            parallelExecutor.pauseWhen {
                if(it.currentInsn == null) true
                else it.currentInsn!!::class in comparableInstructions
            }

            /*
             * Execute the [parallelExecutor] until it has
             * finished executing both methods.
             */
            while(!parallelExecutor.isTerminated()) {
                val isSame = parallelExecutor.executeParallel {
                    val insnA = it.executionA.currentInsn
                    val insnB = it.executionB.currentInsn

                    if(insnA == null || insnB == null) {
                        return@executeParallel false
                    }

                    /*
                     * If the instructions that the executor has paused on because
                     * they are both comparable instructions are the same.
                     *
                     * Increment the score and unpause the executions.
                     * Otherwise, break and move on to the next methods.
                     */
                    if(ExecutionUtil.isSame(matches, methodA, methodB, insnA, insnB)) {
                        score++
                        it.unpause()
                        return@executeParallel true
                    }

                    return@executeParallel false
                }

                if(!isSame) {
                    break
                }

                if(executionA.terminated || executionB.terminated) {
                    break
                }
            }

            matches.executed = true
            matches.score = score

            return matches
        }
    }
}