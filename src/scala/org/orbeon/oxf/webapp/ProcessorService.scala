/**
 * Copyright (C) 2010 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.webapp

import ProcessorService._
import javax.naming.InitialContext
import org.orbeon.exception.OrbeonFormatter
import org.orbeon.oxf.pipeline.InitUtils
import org.orbeon.oxf.pipeline.api.ExternalContext
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.pipeline.api.ProcessorDefinition
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.util.LoggerFactory
import java.io.PrintWriter
import org.orbeon.oxf.util.task.TaskScheduler

class ProcessorService(mainProcessorDefinition: ProcessorDefinition, errorProcessorDefinition: ProcessorDefinition) {

    val jndiContext    = new InitialContext
    val mainProcessor  = InitUtils.createProcessor(mainProcessorDefinition)
    val errorProcessor = Option(errorProcessorDefinition) map InitUtils.createProcessor

    // Run
    def service(pipelineContext: PipelineContext, externalContext: ExternalContext) {

        // NOTE: Should this just be available from the ExternalContext?
        pipelineContext.setAttribute(JNDIContext, jndiContext)

        try InitUtils.runProcessor(mainProcessor, externalContext, pipelineContext, Logger)
        catch {
            case e: Throwable ⇒
                // Log first
                Logger.error(OrbeonFormatter.format(e))

                // Try to start the error pipeline if the response has not been committed yet
                Option(externalContext.getResponse) foreach  { response ⇒
                    if (! response.isCommitted) {
                        response.reset()
                        serviceError(externalContext, e)
                    } else
                        serviceStaticError(externalContext, e)
                }
        }
    }

    private def serviceError(externalContext: ExternalContext, throwable: Throwable): Unit = errorProcessor match {
        case Some(processor) ⇒
            val pipelineContext = new PipelineContext

            // Put top-level throwable so that the exception page can show the Orbeon Forms call stack if available
            if (showExceptions)
                pipelineContext.setAttribute(Throwable, throwable)

            // NOTE: Should this just be available from the ExternalContext?
            pipelineContext.setAttribute(JNDIContext, jndiContext)

            try InitUtils.runProcessor(processor, externalContext, pipelineContext, Logger)
            catch {
                case e: Throwable ⇒
                    Logger.error(OrbeonFormatter.format(e))
                    serviceStaticError(externalContext, throwable)
            }
        case None ⇒
            serviceStaticError(externalContext, throwable)
    }

    private def serviceStaticError(externalContext: ExternalContext, throwable: Throwable): Unit = {
        val sb: StringBuilder = new StringBuilder
        val response  = externalContext.getResponse
        if (! response.isCommitted) {
            // Send new headers and HTML prologue
            response.reset()
            response.setContentType("text/html")
            response.setStatus(ExternalContext.SC_INTERNAL_SERVER_ERROR)
        } else
            // Try to close table that may still be open
            sb.append("</p></table></table></table></table></table>")

        // HTML doc
        sb.append("<html><head><title>Orbeon Forms Error</title></head><body>")
        if (showExceptions) {
            // Show details only if allowed
            sb.append("<pre>")
            sb.append(OrbeonFormatter.format(throwable))
            sb.append("</pre>")
        } else
            sb.append("<p>An error has occurred while processing the request.</p>")

        sb.append("</body></html>")

        val writer =
            try response.getWriter
            catch { case e: IllegalStateException ⇒ new PrintWriter(response.getOutputStream) } // this uses the platform's default encoding, which is not good

        writer.print(sb.toString)
    }

    def destroy(): Unit =
        try {
            TaskScheduler.getInstance.cancelAll(false)
            TaskScheduler.shutdown()
        } catch {
            case error: NoClassDefFoundError ⇒
                // Ignore error: this can happen if using JDK 1.3 (scheduling classed not available)
                // and also happens from time to time on Tomcat 4.1.18 with JDK 1.4.2 (not sure why).
        }
}

object ProcessorService {

    private val HTTPExceptionsProperty = "oxf.http.exceptions"
    private val DefaultHTTPExceptions  = false

    val JNDIContext = "orbeon.jndi-context"
    val Throwable   = "orbeon.throwable"

    val Logger = LoggerFactory.createLogger(classOf[ProcessorService])

    // Whether to show exceptions to the client
    def showExceptions =
        Properties.instance.getPropertySet.getBoolean(HTTPExceptionsProperty, DefaultHTTPExceptions)
}