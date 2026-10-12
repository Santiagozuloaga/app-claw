package androidx.lifecycle

/**
 * STUB SOLO PARA VERIFICACIÓN FUERA DE ANDROID. NO forma parte de la app.
 *
 * Reproduce la superficie de androidx.lifecycle que usa ClawViewModel. A diferencia de un
 * stub vacío, viewModelScope devuelve un CoroutineScope REAL (job + dispatcher), de modo que
 * el bucle de telemetría del init{} y los stateIn() se compilan y se ejecutan como en la app.
 *
 * El CoroutineExceptionHandler es la parte importante: el viewModelScope real lleva
 * SupervisorJob y, si un launch lanza una excepción que nadie captura, ésta llega al handler
 * de la plataforma y derriba la app. Sin registrador aquí, un arnés solo comprobaría que el
 * código "compila", pero no que el fallo queda capturado.
 */

import android.app.Application
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.CopyOnWriteArrayList

object StubUncaughtExceptions {
    val errors = CopyOnWriteArrayList<String>()
    val handler = CoroutineExceptionHandler { _, e ->
        errors += "${e.javaClass.simpleName}: ${e.message}"
    }
    fun clear() = errors.clear()
}

abstract class ViewModel {
    val stubScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default + StubUncaughtExceptions.handler)
    protected open fun onCleared() {}
    fun stubCancelScope() = stubScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
}

abstract class AndroidViewModel(private val application: Application) : ViewModel() {
    fun getApplication(): Application = application
}

val ViewModel.viewModelScope: CoroutineScope get() = stubScope
