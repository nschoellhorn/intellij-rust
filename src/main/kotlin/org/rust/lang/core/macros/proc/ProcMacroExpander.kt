/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.proc

import com.google.common.annotations.VisibleForTesting
import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.rust.ide.experiments.RsExperiments
import org.rust.lang.core.macros.*
import org.rust.lang.core.macros.tt.*
import org.rust.lang.core.parser.createRustPsiBuilder
import org.rust.openapiext.RsPathManager
import org.rust.openapiext.isFeatureEnabled
import org.rust.stdext.*
import org.rust.stdext.RsResult.Err
import org.rust.stdext.RsResult.Ok
import java.io.*
import java.nio.file.Path
import java.util.concurrent.*
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class ProcMacroExpander(
    private val project: Project,
    private val server: ProcMacroServer? = ProcMacroApplicationService.getInstance().getServer()
) : MacroExpander<RsProcMacroData, ProcMacroExpansionError>() {

    override fun expandMacroAsTextWithErr(
        def: RsProcMacroData,
        call: RsMacroCallData
    ): RsResult<Pair<CharSequence, RangeMap>, ProcMacroExpansionError> {
        val macroCallBodyText = call.macroBody ?: return Err(ProcMacroExpansionError.MacroCallSyntax)
        val (macroCallBody, tokenMap) = project.createRustPsiBuilder(macroCallBodyText).parseSubtree()
        return expandWithErr(macroCallBody, def.name, def.artifact.path.toString()).map {
            MappedSubtree(it, tokenMap).toMappedText()
        }
    }

    fun expandWithErr(macroCallBody: SubtreeS, macroName: String, lib: String): RsResult<SubtreeS, ProcMacroExpansionError> {
        val server = server ?: return Err(ProcMacroExpansionError.ExecutableNotFound)
        val response = try {
            server.send(Request.ExpansionMacro(ExpansionTask(macroCallBody, macroName, null, lib)))
        } catch (ignored: TimeoutException) {
            return Err(ProcMacroExpansionError.Timeout)
        } catch (e: ProcessCreationException) {
            MACRO_LOG.warn("Failed to run proc macro expander", e)
            return Err(ProcMacroExpansionError.CantRunExpander)
        } catch (e: IOException) {
            return Err(ProcMacroExpansionError.ExceptionThrown(e))
        } catch (e: JsonParseException) {
            return Err(ProcMacroExpansionError.ExceptionThrown(e))
        }
        return when (response) {
            is Response.ExpansionMacro -> Ok(response.expansionResult.expansion)
            is Response.Error -> Err(ProcMacroExpansionError.Expansion(response.error.message))
        }
    }

    companion object {
        const val EXPANDER_VERSION: Int = 0;
    }
}

@Service
class ProcMacroApplicationService : Disposable {

    private var sharedServer: ProcMacroServer? = null

    @Synchronized
    fun getServer(): ProcMacroServer? {
        if (!isFeatureEnabled(RsExperiments.PROC_MACROS)) return null

        var server = sharedServer
        if (server == null) {
            server = ProcMacroServer.tryCreate()
            if (server != null) {
                Disposer.register(this, server)
            }
            sharedServer = server
        }
        return server
    }

    override fun dispose() {}

    companion object {
        fun getInstance(): ProcMacroApplicationService = service()
    }
}

class ProcMacroServer private constructor(expanderExecutable: Path) : Disposable {
    private val pool = Pool(4) {
        ProcMacroServerProcess.createAndRun(expanderExecutable) // Throws ProcessCreationException
    }

    init {
        Disposer.register(this, pool)
    }

    @Throws(ProcessCreationException::class, IOException::class, JsonParseException::class, TimeoutException::class)
    fun send(request: Request): Response {
        val io = pool.alloc() // Throws ProcessCreationException
        return try {
            io.send(request) // Throws IOException, JsonParseException, TimeoutException
        } finally {
            pool.free(io)
        }
    }

    override fun dispose() {}

    companion object {
        fun tryCreate(): ProcMacroServer? {
            val expanderExecutable = RsPathManager.nativeHelper()
            if (expanderExecutable == null || !expanderExecutable.isExecutable()) {
                return null
            }
            return createUnchecked(expanderExecutable)
        }

        @VisibleForTesting
        fun createUnchecked(expanderExecutable: Path): ProcMacroServer {
            return ProcMacroServer(expanderExecutable)
        }
    }
}

private class Pool(private val limit: Int, private val supplier: () -> ProcMacroServerProcess) : Disposable {
    private val stack: MutableList<Container<ProcMacroServerProcess>> = mutableListOf()
    private val stackLock: Lock = ReentrantLock()
    private val stackIsNotEmpty: Condition = stackLock.newCondition()

    @Volatile
    private var isDisposed: Boolean = false

    init {
        repeat(limit) {
            stack.add(Container.Invalid)
        }
    }

    fun alloc(): ProcMacroServerProcess {
        check(!isDisposed)
        val value = stackLock.withLockAndCheckingCancelled {
            while (stack.isEmpty()) {
                stackIsNotEmpty.awaitWithCheckCancelled()
            }
            stack.removeLast()
        }
        return when (value) {
            is Container.Valid -> if (value.t.isValid) {
                value.t
            } else {
                Disposer.dispose(value.t)
                supply()
            }
            Container.Invalid -> supply()
        }
    }

    private fun supply(): ProcMacroServerProcess {
        val newValue = try {
            supplier()
        } catch (t: Throwable) {
            free(Container.Invalid)
            throw t
        }
        Disposer.register(this, newValue)
        return newValue
    }

    fun free(t: ProcMacroServerProcess) {
        check(!isDisposed)
        free(Container.Valid(t))
    }

    private fun free(t: Container<ProcMacroServerProcess>) {
        stackLock.withLock {
            stack.add(t)
            stackIsNotEmpty.signal()
        }
    }

    override fun dispose() {
        isDisposed = true
        if (stack.size != limit) {
            MACRO_LOG.error("Some processes were not freed! ${stack.size} != $limit")
        }
    }

    private sealed class Container<out T : Any> {
        class Valid<T : Any>(val t: T) : Container<T>()
        object Invalid : Container<Nothing>()
    }
}

private class ProcMacroServerProcess private constructor(
    private val process: Process,
    private val stdout: Reader,
    private val stdin: Writer,
    private val timeout: Long = 5000,
) : Runnable, Disposable {
    private val lock = ReentrantLock()
    private val inq = SynchronousQueue<Pair<Request, CompletableFuture<Response>>>()
    private val task = ProcessIOExecutorService.INSTANCE.submit(this)
    @Volatile
    private var isDisposed: Boolean = false

    @Throws(IOException::class, JsonParseException::class, TimeoutException::class)
    fun send(request: Request): Response {
        return try {
            lock.withLockAndCheckingCancelled {
                if (!process.isAlive) throw IOException("The process has been killed")
                val responder = CompletableFuture<Response>()
                if (!inq.offer(request to responder, timeout, TimeUnit.MILLISECONDS)) {
                    throw TimeoutException()
                }

                try {
                    // throws TimeoutException, ExecutionException, InterruptedException
                    responder.getWithCheckCanceled(timeout)
                } catch (e: ExecutionException) {
                    // Unwrap exceptions from `writeAndRead` method
                    throw e.cause ?: IllegalStateException("Unexpected ExecutionException without a cause", e)
                } catch (e: InterruptedException) {
                    // Should not really happens
                    throw ProcessCanceledException(e)
                }
            }
        } catch (t: Throwable) {
            Disposer.dispose(this)
            throw t
        }
    }

    val isValid: Boolean
        get() = !isDisposed && process.isAlive

    override fun run() {
        try {
            while (!isDisposed) {
                val (request, responder) = try {
                    inq.take() // Blocks until request is available or the `task` is cancelled
                } catch (ignored: InterruptedException) {
                    return // normal shutdown
                }
                val response = try {
                    writeAndRead(request)
                } catch (e: Throwable) {
                    responder.completeExceptionally(e)
                    return
                }
                responder.complete(response)
            }
        } finally {
            process.destroyForcibly() // SIGKILL
        }
    }

    @Throws(IOException::class, JsonParseException::class)
    private fun writeAndRead(request: Request): Response {
        stdin.write(gson.toJson(request))
        stdin.write("\n")
        stdin.flush()

        return gson.fromJson(gson.newJsonReader(stdout), Response::class.java)
            ?: throw EOFException()
    }

    override fun dispose() {
        isDisposed = true
        task.cancel(true)
        process.destroyForcibly() // SIGKILL
    }

    companion object {
        private val gson = GsonBuilder()
            .serializeNulls()
            .registerTypeAdapter(Response::class.java, ResponseAdapter())
            .registerTypeAdapter(TokenTree::class.java, TokenTreeAdapter())
            .registerTypeAdapter(LeafS::class.java, LeafSAdapter())
            .create()

        @Throws(ProcessCreationException::class)
        fun createAndRun(expanderExecutable: Path): ProcMacroServerProcess {
            MACRO_LOG.debug { "Starting proc macro expander process $expanderExecutable" }
            val process: Process = try {
                ProcessBuilder(expanderExecutable.toString())
                    // Let a proc macro know that it is ran from intellij-rust
                    .apply { environment()["INTELLIJ_RUST"] = "true" }
                    .start()
            } catch (e: IOException) {
                throw ProcessCreationException(e)
            }

            MACRO_LOG.info("Started proc macro expander process (pid: ${process.pid()})")

            return ProcMacroServerProcess(
                process,
                InputStreamReader(process.inputStream),
                OutputStreamWriter(process.outputStream)
            )
        }
    }
}

private class ProcessCreationException : RuntimeException {
    constructor() : super()
    constructor(cause: IOException) : super(cause)
}
