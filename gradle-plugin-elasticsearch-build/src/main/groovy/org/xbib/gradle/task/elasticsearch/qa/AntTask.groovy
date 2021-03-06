package org.xbib.gradle.task.elasticsearch.qa

import org.apache.tools.ant.BuildListener
import org.apache.tools.ant.BuildLogger
import org.apache.tools.ant.DefaultLogger
import org.apache.tools.ant.Project
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

import java.nio.charset.Charset

/**
 * A task which will run ant commands.
 *
 * Logging for the task is customizable for subclasses by overriding makeLogger.
 */
abstract class AntTask extends DefaultTask {

    /**
     * A buffer that will contain the output of the ant code run,
     * if the output was not already written directly to stdout.
     */
    public final ByteArrayOutputStream outputBuffer = new ByteArrayOutputStream()

    @TaskAction
    final void executeTask() {
        AntBuilder ant = new AntBuilder()

        // remove existing loggers, we add our own
        List<BuildLogger> toRemove = new ArrayList<>();
        for (BuildListener listener : ant.project.getBuildListeners()) {
            if (listener instanceof BuildLogger) {
                toRemove.add(listener);
            }
        }
        for (BuildLogger listener : toRemove) {
            ant.project.removeBuildListener(listener)
        }

        // otherwise groovy replaces System.out, and you have no chance to debug
        // ant.saveStreams = false

        final int outputLevel = logger.isDebugEnabled() ? Project.MSG_DEBUG : Project.MSG_INFO
        final PrintStream stream = useStdout() ? System.out : new PrintStream(outputBuffer, true, Charset.defaultCharset().name())
        BuildLogger antLogger = makeLogger(stream, outputLevel)

        ant.project.addBuildListener(antLogger)
        try {
            runAnt(ant)
        } catch (Exception e) {
            // ant failed, so see if we have buffered output to emit, then rethrow the failure
            String buffer = outputBuffer.toString()
            if (!buffer.isEmpty()) {
                logger.error("=== Ant output ===\n${buffer}")
            }
            throw e
        }
    }

    /** Runs the doAnt closure. This can be overridden by subclasses instead of having to set a closure. */
    protected abstract void runAnt(AntBuilder ant)

    /** Create the logger the ant runner will use, with the given stream for error/output. */
    protected BuildLogger makeLogger(PrintStream stream, int outputLevel) {
        return new DefaultLogger(
            errorPrintStream: stream,
            outputPrintStream: stream,
            messageOutputLevel: outputLevel)
    }

    /**
     * Returns true if the ant logger should write to stdout, or false if to the buffer.
     * The default implementation writes to the buffer when gradle info logging is disabled.
     */
    protected boolean useStdout() {
        return logger.isInfoEnabled()
    }
}
